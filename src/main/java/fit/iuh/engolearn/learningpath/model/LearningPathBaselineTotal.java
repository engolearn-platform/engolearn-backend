package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;

@Setter
public class LearningPathBaselineTotal {

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
