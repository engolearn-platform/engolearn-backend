package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import lombok.Setter;

@Setter
public class LearningPathCancel {

    private Date at;
    private String reason;

    public Date getAt() {
        return at;
    }

    public String getReason() {
        return reason;
    }
}
