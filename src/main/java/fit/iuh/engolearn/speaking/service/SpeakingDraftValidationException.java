package fit.iuh.engolearn.speaking.service;

public class SpeakingDraftValidationException extends IllegalArgumentException {
    public SpeakingDraftValidationException(String message) {
        super(message);
    }

    public SpeakingDraftValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
