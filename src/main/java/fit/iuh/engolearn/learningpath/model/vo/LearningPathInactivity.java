package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import java.util.Date;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathInactivity implements EmbeddedDocument {

    @Field("anchor_date")
    private String anchorDate;

    @Field("cancel_at")
    private Date cancelAt;

    public String getAnchorDate() {
        return anchorDate;
    }

    public Date getCancelAt() {
        return cancelAt;
    }
}
