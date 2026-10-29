package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;
import java.util.Date;
import java.util.List;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathWeek implements EmbeddedDocument {

    @Field("week_no")
    private Integer weekNo;

    private LearningPathWeekStatus status;

    @Field("opened_at")
    private Date openedAt;

    @Field("close_at")
    private Date closeAt;

    @Field("closed_at")
    private Date closedAt;

    @Field("close_reason")
    private LearningPathWeekCloseReason closeReason;

    private LearningPathProgress progress;
    private LearningPathWeekStats stats;
    private List<LearningPathDay> days;

    public Integer getWeekNo() {
        return weekNo;
    }

    public LearningPathWeekStatus getStatus() {
        return status;
    }

    public Date getOpenedAt() {
        return openedAt;
    }

    public Date getCloseAt() {
        return closeAt;
    }

    public Date getClosedAt() {
        return closedAt;
    }

    public LearningPathWeekCloseReason getCloseReason() {
        return closeReason;
    }

    public LearningPathProgress getProgress() {
        return progress;
    }

    public LearningPathWeekStats getStats() {
        return stats;
    }

    public List<LearningPathDay> getDays() {
        return days;
    }
}
