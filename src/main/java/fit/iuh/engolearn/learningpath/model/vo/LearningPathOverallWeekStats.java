package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathOverallWeekStats implements EmbeddedDocument {

    private Integer assigned;
    private Integer answered;
    private Integer correct;

    @Field("completion_rate")
    private Double completionRate;

    @Field("practice_score")
    private Double practiceScore;

    @Field("compared_to")
    private LearningPathComparedTo comparedTo;

    @Field("improvement_pp")
    private Double improvementPp;

    public Integer getAssigned() {
        return assigned;
    }

    public Integer getAnswered() {
        return answered;
    }

    public Integer getCorrect() {
        return correct;
    }

    public Double getCompletionRate() {
        return completionRate;
    }

    public Double getPracticeScore() {
        return practiceScore;
    }

    public LearningPathComparedTo getComparedTo() {
        return comparedTo;
    }

    public Double getImprovementPp() {
        return improvementPp;
    }
}
