package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;

@Setter
public class LearningPathBaselineTotal implements EmbeddedDocument {

    private Integer correct;
    private Integer total;
    private Double percent;

    public Integer getCorrect() {
        return correct;
    }

    public Integer getTotal() {
        return total;
    }

    public Double getPercent() {
        return percent;
    }
}
