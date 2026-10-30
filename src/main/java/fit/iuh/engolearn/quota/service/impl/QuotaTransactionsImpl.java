package fit.iuh.engolearn.quota.service.impl;
import fit.iuh.engolearn.quota.service.QuotaTransactions;
import lombok.RequiredArgsConstructor;

import com.mongodb.MongoException;
import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

@Service
@RequiredArgsConstructor
public class QuotaTransactionsImpl implements QuotaTransactions {
    private final ObjectProvider<MongoTransactionManager> managers;


    public boolean available() { return managers.getIfAvailable() != null; }

    public <T> T execute(Supplier<T> action) { return execute(action, () -> null); }

    public <T> T event(Supplier<T> action) {
        MongoTransactionManager manager = managers.getIfAvailable();
        if (manager == null) throw new QuotaUnavailableException();
        // AFTER_COMMIT may still have bound resources from the already committed publisher.
        TransactionTemplate suspended = new TransactionTemplate(manager);
        suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return suspended.execute(status -> execute(action));
    }

    public <T> T execute(Supplier<T> action, Supplier<T> acceptedLookup) {
        MongoTransactionManager manager = managers.getIfAvailable();
        if (manager == null) throw new QuotaUnavailableException();
        // The owner retries the entire business action when Quota joins an existing transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return action.get();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return new TransactionTemplate(manager).execute(status -> action.get());
            } catch (RuntimeException failure) {
                if (unknownCommit(failure)) {
                    try {
                        T accepted = acceptedLookup.get();
                        if (accepted != null) return accepted;
                    } catch (RuntimeException lookupFailure) { throw new QuotaUnavailableException(lookupFailure); }
                    throw new QuotaUnavailableException(failure);
                }
                if (!retryable(failure)) throw failure;
                if (attempt == 2) throw new QuotaUnavailableException(failure);
            }
        }
        throw new QuotaUnavailableException();
    }

    private boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException || cause instanceof OptimisticLockingFailureException) return true;
            if (cause instanceof MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError")
                    || mongo.getCode() == 112 || mongo.getCode() == 11000)) return true;
        }
        return false;
    }

    private boolean unknownCommit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && mongo.hasErrorLabel("UnknownTransactionCommitResult")) return true;
        }
        return false;
    }
}
