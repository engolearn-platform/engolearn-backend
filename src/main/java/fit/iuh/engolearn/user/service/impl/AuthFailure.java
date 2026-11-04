package fit.iuh.engolearn.user.service.impl;

public class AuthFailure extends RuntimeException {
    private final String code;
    private final int status;
    public AuthFailure(String code) { this(code, "INVALID_CREDENTIALS", 401); }
    public AuthFailure(String message, String code, int status) { super(message); this.code = code; this.status = status; }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}
