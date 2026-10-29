package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import java.util.Date;
import java.util.List;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathDay implements EmbeddedDocument {

    @Field("day_no")
    private Integer dayNo;

    private LearningPathDayStatus status;

    @Field("started_at")
    private Date startedAt;

    @Field("started_date")
    private String startedDate;

    @Field("auto_close_at")
    private Date autoCloseAt;

    @Field("closed_at")
    private Date closedAt;

    @Field("close_reason")
    private LearningPathDayCloseReason closeReason;

    private LearningPathProgress progress;
    private LearningPathDayResult result;
    private List<LearningPathDayItem> items;

    public Integer getDayNo() {
        return dayNo;
    }

    public LearningPathDayStatus getStatus() {
        return status;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public String getStartedDate() {
        return startedDate;
    }

    public Date getAutoCloseAt() {
        return autoCloseAt;
    }

    public Date getClosedAt() {
        return closedAt;
    }

    public LearningPathDayCloseReason getCloseReason() {
        return closeReason;
    }

    public LearningPathProgress getProgress() {
        return progress;
    }

    public LearningPathDayResult getResult() {
        return result;
    }

    public List<LearningPathDayItem> getItems() {
        return items;
    }
}
