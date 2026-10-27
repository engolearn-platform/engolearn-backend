package fit.iuh.engolearn.learningpath.model;

import java.util.List;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathWeekStats {

    @Field("by_micro_skill")
    private List<LearningPathMicroSkillWeekStats> byMicroSkill;

    private LearningPathOverallWeekStats overall;

    public List<LearningPathMicroSkillWeekStats> getByMicroSkill() {
        return byMicroSkill;
    }

    public LearningPathOverallWeekStats getOverall() {
        return overall;
    }
}
