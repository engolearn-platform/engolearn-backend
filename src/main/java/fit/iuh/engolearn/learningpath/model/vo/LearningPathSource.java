package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import java.util.Date;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathSource implements EmbeddedDocument {

    @Field("attempt_id")
    private ObjectId attemptId;

    @Field("test_set_id")
    private ObjectId testSetId;

    @Field("taken_at")
    private Date takenAt;

    public ObjectId getAttemptId() {
        return attemptId;
    }

    public ObjectId getTestSetId() {
        return testSetId;
    }

    public Date getTakenAt() {
        return takenAt;
    }
}
