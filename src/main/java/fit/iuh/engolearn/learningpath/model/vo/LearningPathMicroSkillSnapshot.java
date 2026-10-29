package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathMicroSkillSnapshot implements EmbeddedDocument {

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
