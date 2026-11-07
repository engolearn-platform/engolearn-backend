package fit.iuh.engolearn.assessment.exception;

public class AssessmentException extends RuntimeException {
    private final int status;
    private final String code;
    public AssessmentException(int status, String code, String message) {
        super(message); this.status=status; this.code=code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public AssessmentException(int status,String code,String message,Throwable cause) {
        super(message,cause);this.status=status;this.code=code;
    }
}
