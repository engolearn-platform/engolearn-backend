package fit.iuh.engolearn.learningpath.exception;

public class QuestionPoolPassageNotFoundException extends RuntimeException {
    public QuestionPoolPassageNotFoundException() {
        super("Passage was not found");
    }
}
