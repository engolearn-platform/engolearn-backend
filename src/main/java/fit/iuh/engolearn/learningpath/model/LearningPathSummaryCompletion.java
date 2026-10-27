package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathSummaryCompletion {

    private Integer assigned;
    private Integer answered;

    @Field("completion_rate")
    private Double completionRate;

    public Integer getAssigned() {
        return assigned;
    }

    public Integer getAnswered() {
        return answered;
    }

    public Double getCompletionRate() {
        return completionRate;
    }
}
