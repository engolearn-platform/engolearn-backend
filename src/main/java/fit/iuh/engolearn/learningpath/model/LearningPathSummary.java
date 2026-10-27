package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import java.util.List;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathSummary {

    @Field("by_micro_skill")
    private List<LearningPathSummaryMicroSkill> byMicroSkill;

    private LearningPathSummaryCompletion completion;

    @Field("computed_at")
    private Date computedAt;

    public List<LearningPathSummaryMicroSkill> getByMicroSkill() {
        return byMicroSkill;
    }

    public LearningPathSummaryCompletion getCompletion() {
        return completion;
    }

    public Date getComputedAt() {
        return computedAt;
    }
}
