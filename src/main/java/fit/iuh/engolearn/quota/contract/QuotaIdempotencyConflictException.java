package fit.iuh.engolearn.quota.contract;

public class QuotaIdempotencyConflictException extends RuntimeException {
    public QuotaIdempotencyConflictException() { super("Idempotency key was used with a different payload"); }
}
