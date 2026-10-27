package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathMicroSkillSnapshot {

    @Field("micro_skill")
    private String microSkill;

    private String skill;
    private LearningPathBaseline baseline;

    public String getMicroSkill() {
        return microSkill;
    }

    public String getSkill() {
        return skill;
    }

    public LearningPathBaseline getBaseline() {
        return baseline;
    }
}
