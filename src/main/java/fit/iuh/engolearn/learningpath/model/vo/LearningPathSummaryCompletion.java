package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathSummaryCompletion implements EmbeddedDocument {

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
