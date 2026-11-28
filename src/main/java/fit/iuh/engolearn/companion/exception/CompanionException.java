package fit.iuh.engolearn.companion.exception;

import lombok.Getter;

@Getter
public class CompanionException extends RuntimeException {
    private final int status;
    private final String code;
    private final String safeDetail;
    private final boolean retryable;

    public CompanionException(int status, String code, String safeDetail, boolean retryable) {
        super(safeDetail);
        this.status = status;
        this.code = code;
        this.safeDetail = safeDetail;
        this.retryable = retryable;
    }
}
