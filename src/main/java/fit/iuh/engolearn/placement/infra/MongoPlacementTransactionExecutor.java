package fit.iuh.engolearn.placement.infra;

import com.mongodb.MongoException;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class MongoPlacementTransactionExecutor implements PlacementTransactionExecutor {
    private static final int MAX_ATTEMPTS = 5;
    private final TransactionTemplate transactions;

    @Override
    public <T> T execute(Supplier<T> action) {
        // Claim intentionally catches a deadline domain exception; joining directly avoids rollback-only poisoning.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return action.get();
        for (int attempt = 0; ; attempt++) {
            try {
                return transactions.execute(status -> action.get());
            } catch (RuntimeException failure) {
                if (attempt + 1 >= MAX_ATTEMPTS || !retryable(failure)) throw failure;
                try { Thread.sleep(20L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Placement transaction retry interrupted", interrupted);
                }
            }
        }
    }

    private boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError")
                    || mongo.hasErrorLabel("UnknownTransactionCommitResult") || mongo.getCode() == 112
                    || mongo.getCode() == 251)) return true;
        }
        return false;
    }
}
