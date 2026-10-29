package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathDayResult implements EmbeddedDocument {

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
