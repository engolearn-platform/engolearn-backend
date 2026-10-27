package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import java.util.List;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathWeek {

    @Field("week_no")
    private Integer weekNo;

    private String status;

    @Field("opened_at")
    private Date openedAt;

    @Field("close_at")
    private Date closeAt;

    @Field("closed_at")
    private Date closedAt;

    @Field("close_reason")
    private String closeReason;

    private LearningPathProgress progress;
    private LearningPathWeekStats stats;
    private List<LearningPathDay> days;

    public Integer getWeekNo() {
        return weekNo;
    }

    public String getStatus() {
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

    public String getCloseReason() {
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
