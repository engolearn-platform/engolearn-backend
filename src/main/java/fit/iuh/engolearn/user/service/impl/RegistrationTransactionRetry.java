package fit.iuh.engolearn.user.service.impl;

import com.mongodb.MongoException;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class RegistrationTransactionRetry {
    private RegistrationTransactionRetry() {}

    // The supplier owns the entire transaction. A new snapshot lets the unique email index
    // return the committed duplicate when another enrollment won the first insert.
    static <T> T run(Supplier<T> registration) {
        for (int attempt = 0; ; attempt++) {
            try {
                return registration.get();
            } catch (RuntimeException failure) {
                if (!abortedWriteConflict(failure) || TransactionSynchronizationManager.isActualTransactionActive())
                    throw failure;
                if (attempt == 6) throw unavailable();
                try {
                    Thread.sleep(25L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw unavailable();
                }
            }
        }
    }

    private static boolean abortedWriteConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo)
                return mongo.getCode() == 112 && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        && !mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        }
        return false;
    }

    private static AuthFailure unavailable() {
        return new AuthFailure("Account setup is temporarily unavailable", "ACCOUNT_SETUP_UNAVAILABLE", 503);
    }
}
