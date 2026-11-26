package fit.iuh.engolearn.learningpath.utils;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import java.util.Map;

public final class QuestionPoolMicroSkills {
    private static final Map<String, QuestionPoolSkill> SKILLS = Map.of(
            "Listening for Gist", QuestionPoolSkill.LISTENING,
            "Listening for Specific Information", QuestionPoolSkill.LISTENING,
            "Listening for Detail & Relationship", QuestionPoolSkill.LISTENING,
            "Listening for Inference", QuestionPoolSkill.LISTENING,
            "Listening for Attitude / Purpose", QuestionPoolSkill.LISTENING,
            "Reading for Gist", QuestionPoolSkill.READING,
            "Reading for Specific Information", QuestionPoolSkill.READING,
            "Reading for Detail & Relationship", QuestionPoolSkill.READING,
            "Vocabulary in Context", QuestionPoolSkill.READING,
            "Inference & Author's View", QuestionPoolSkill.READING);

    private QuestionPoolMicroSkills() {}

    public static boolean belongsTo(String microSkill, String skill) {
        var owner = microSkill == null ? null : SKILLS.get(microSkill);
        return owner != null && owner.name().equals(skill);
    }
}
