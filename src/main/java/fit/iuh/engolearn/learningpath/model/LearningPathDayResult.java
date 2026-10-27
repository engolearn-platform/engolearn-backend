package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathDayResult {

    private Integer wrong;

    @Field("completion_rate")
    private Double completionRate;

    @Field("practice_score")
    private Double practiceScore;

    public Integer getWrong() {
        return wrong;
    }

    public Double getCompletionRate() {
        return completionRate;
    }

    public Double getPracticeScore() {
        return practiceScore;
    }
}
