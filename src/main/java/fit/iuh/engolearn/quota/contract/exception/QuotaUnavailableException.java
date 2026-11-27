package fit.iuh.engolearn.quota.contract.exception;

public class QuotaUnavailableException extends RuntimeException {
    private final boolean missingConfiguration;

    public QuotaUnavailableException() { this(false, null); }
    public QuotaUnavailableException(Throwable cause) { this(false, cause); }
    public QuotaUnavailableException(boolean missingConfiguration) { this(missingConfiguration, null); }

    private QuotaUnavailableException(boolean missingConfiguration, Throwable cause) {
        super("Quota is temporarily unavailable", cause);
        this.missingConfiguration = missingConfiguration;
    }

    public boolean missingConfiguration() { return missingConfiguration; }
}
