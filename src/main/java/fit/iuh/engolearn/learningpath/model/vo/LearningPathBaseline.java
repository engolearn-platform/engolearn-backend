package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathBaseline implements EmbeddedDocument {

    private Integer correct;
    private Integer total;
    private Double percent;
    private String band;

    @Field("result_id")
    private ObjectId resultId;

    public Integer getCorrect() {
        return correct;
    }

    public Integer getTotal() {
        return total;
    }

    public Double getPercent() {
        return percent;
    }

    public String getBand() {
        return band;
    }

    public ObjectId getResultId() {
        return resultId;
    }
}
