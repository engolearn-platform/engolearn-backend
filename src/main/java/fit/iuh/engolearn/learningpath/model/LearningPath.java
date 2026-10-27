
package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import java.util.List;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "learning_paths")
@Setter
public class LearningPath {

    @Id
    private ObjectId id;

    @Field("user_id")
    private ObjectId userId;

    private LearningPathSource source;

    @Field("created_at")
    private Date createdAt;

    @Field("created_date")
    private String createdDate;

    @Field("ended_at")
    private Date endedAt;

    private String level;
    private Integer k;
    private String status;

    @Field("is_active")
    private Boolean active;

    @Field("current_week_no")
    private Integer currentWeekNo;

    private LearningPathConfig config;

    @Field("micro_skills")
    private List<LearningPathMicroSkillSnapshot> microSkills;

    @Field("baseline_total")
    private LearningPathBaselineTotal baselineTotal;

    private LearningPathProgress progress;

    private LearningPathInactivity inactivity;

    @Field("next_run_at")
    private Date nextRunAt;

    private List<LearningPathWeek> weeks;
    private LearningPathSummary summary;
    private LearningPathCancel cancel;

    public ObjectId getId() {
        return id;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public LearningPathSource getSource() {
        return source;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public Date getEndedAt() {
        return endedAt;
    }

    public String getLevel() {
        return level;
    }

    public Integer getK() {
        return k;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getActive() {
        return active;
    }

    public Integer getCurrentWeekNo() {
        return currentWeekNo;
    }

    public LearningPathConfig getConfig() {
        return config;
    }

    public List<LearningPathMicroSkillSnapshot> getMicroSkills() {
        return microSkills;
    }

    public LearningPathBaselineTotal getBaselineTotal() {
        return baselineTotal;
    }

    public LearningPathProgress getProgress() {
        return progress;
    }

    public LearningPathInactivity getInactivity() {
        return inactivity;
    }

    public Date getNextRunAt() {
        return nextRunAt;
    }

    public List<LearningPathWeek> getWeeks() {
        return weeks;
    }

    public LearningPathSummary getSummary() {
        return summary;
    }

    public LearningPathCancel getCancel() {
        return cancel;
    }
}
