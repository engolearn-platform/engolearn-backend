package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;

@Setter
public class LearningPathProgress {

    private Integer assigned;
    private Integer answered;
    private Integer correct;

    public Integer getAssigned() {
        return assigned;
    }

    public Integer getAnswered() {
        return answered;
    }

    public Integer getCorrect() {
        return correct;
    }
}
