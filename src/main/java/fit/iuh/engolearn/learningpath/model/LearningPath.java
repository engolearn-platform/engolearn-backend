
package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathBaselineTotal;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathCancel;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathConfig;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathInactivity;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathProgress;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSource;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSummary;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.shared.BaseDocument;
import java.util.Date;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "learning_paths")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@CompoundIndexes({
    @CompoundIndex(name = "uq_learning_path_assessment_attempt", def = "{'source.assessment_attempt_id':1}",
            unique = true, partialFilter = "{'source.assessment_attempt_id':{'$type':'string'}}"),
    @CompoundIndex(name = "uq_active_path_user_micro_skill", def = "{'user_id':1,'micro_skills.micro_skill':1}",
            unique = true, partialFilter = "{'is_active':true}")
})
public class LearningPath extends BaseDocument {

    @Field("revision")
    private Long revision;

    @Field("user_id")
    private String userId;

    @Field("source")
    private LearningPathSource source;

    @Field("created_date")
    private String createdDate;

    @Field("ended_at")
    private Date endedAt;

    @Field("level")
    private String level;
    @Field("k")
    private Integer k;
    @Field("status")
    private LearningPathStatus status;

    @Field("is_active")
    private Boolean active;

    @Field("current_week_no")
    private Integer currentWeekNo;

    @Field("config")
    private LearningPathConfig config;

    @Field("micro_skills")
    private List<LearningPathMicroSkillSnapshot> microSkills;

    @Field("baseline_total")
    private LearningPathBaselineTotal baselineTotal;

    @Field("progress")
    private LearningPathProgress progress;

    @Field("inactivity")
    private LearningPathInactivity inactivity;

    @Field("next_run_at")
    private Date nextRunAt;

    @Field("weeks")
    private List<LearningPathWeek> weeks;
    @Field("summary")
    private LearningPathSummary summary;
    @Field("cancel")
    private LearningPathCancel cancel;

}
