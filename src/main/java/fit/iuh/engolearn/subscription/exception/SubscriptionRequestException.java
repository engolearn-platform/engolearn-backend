package fit.iuh.engolearn.subscription.exception;
public class SubscriptionRequestException extends RuntimeException {
    private final int status;
    private final String code;
    private final java.util.List<FieldError> fieldErrors;
    public record FieldError(String field,String message) {}
    public SubscriptionRequestException(int status, String code, String detail) { this(status,code,detail,java.util.List.of()); }
    public SubscriptionRequestException(int status,String code,String detail,java.util.List<FieldError> fieldErrors) {super(detail);this.status=status;this.code=code;this.fieldErrors=java.util.List.copyOf(fieldErrors);}
    public java.util.List<FieldError> fieldErrors(){return fieldErrors;}
    public int status() { return status; }
    public String code() { return code; }
}
