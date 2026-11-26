package fit.iuh.engolearn.companion.exception;

import fit.iuh.engolearn.companion.exception.CompanionException;

public class CompanionMemoryException extends CompanionException {
    public CompanionMemoryException(int status, String code, String detail, boolean retryable) {
        super(status, code, detail, retryable);
    }
}
