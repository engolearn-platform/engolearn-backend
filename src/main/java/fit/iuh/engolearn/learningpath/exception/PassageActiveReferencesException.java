package fit.iuh.engolearn.learningpath.exception;

import lombok.Getter;

@Getter
public class PassageActiveReferencesException extends RuntimeException {
    private final long activeQuestionCount;

    public PassageActiveReferencesException(long activeQuestionCount) {
        super("Passage is still referenced by ACTIVE questions");
        this.activeQuestionCount = activeQuestionCount;
    }
}
