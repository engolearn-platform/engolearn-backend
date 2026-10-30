package fit.iuh.engolearn.quota.contract.exception;

public class QuotaUnavailableException extends RuntimeException {
    public QuotaUnavailableException() { super("Quota is temporarily unavailable"); }
    public QuotaUnavailableException(Throwable cause) { super("Quota is temporarily unavailable", cause); }
}
