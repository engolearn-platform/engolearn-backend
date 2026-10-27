package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathSummaryMicroSkill {

    @Field("micro_skill")
    private String microSkill;

    @Field("baseline_percent")
    private Double baselinePercent;

    private Integer answered;
    private Integer correct;

    @Field("final_practice_score")
    private Double finalPracticeScore;

    @Field("improvement_pp")
    private Double improvementPp;

    public String getMicroSkill() {
        return microSkill;
    }

    public Double getBaselinePercent() {
        return baselinePercent;
    }

    public Integer getAnswered() {
        return answered;
    }

    public Integer getCorrect() {
        return correct;
    }

    public Double getFinalPracticeScore() {
        return finalPracticeScore;
    }

    public Double getImprovementPp() {
        return improvementPp;
    }
}
