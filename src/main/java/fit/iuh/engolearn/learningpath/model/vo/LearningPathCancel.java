package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathCancelReason;
import java.util.Date;
import lombok.Setter;

@Setter
public class LearningPathCancel implements EmbeddedDocument {

    private Date at;
    private LearningPathCancelReason reason;

    public Date getAt() {
        return at;
    }

    public LearningPathCancelReason getReason() {
        return reason;
    }
}
