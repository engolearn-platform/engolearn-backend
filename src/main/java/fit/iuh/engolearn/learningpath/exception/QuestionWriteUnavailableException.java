package fit.iuh.engolearn.learningpath.exception;

public class QuestionWriteUnavailableException extends RuntimeException {
    public QuestionWriteUnavailableException(String message) { super(message); }
    public QuestionWriteUnavailableException(Throwable cause) { super("Question write unavailable", cause); }
}
