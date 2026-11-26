package fit.iuh.engolearn.learningpath.exception;

public class QuestionPoolQuestionNotFoundException extends RuntimeException {
    public QuestionPoolQuestionNotFoundException() {
        super("Question was not found");
    }
}
