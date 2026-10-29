package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;

@Setter
public class LearningPathProgress implements EmbeddedDocument {

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
