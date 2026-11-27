package fit.iuh.engolearn.assessment.contract;

import java.util.Arrays;
import java.util.Optional;

/** Skill codes with an agreed Assessment to Learning Path mapping. */
public enum AssessmentSkillCatalog {
    R01("READING", "Reading for Specific Information"),
    R02("READING", "Reading for Gist"),
    R04("READING", "Inference & Author's View"),
    L01("LISTENING", "Listening for Gist"),
    L03("LISTENING", "Listening for Specific Information");

    private final String section;
    private final String learningPathName;

    AssessmentSkillCatalog(String section, String learningPathName) {
        this.section = section;
        this.learningPathName = learningPathName;
    }

    public String section() { return section; }
    public String learningPathName() { return learningPathName; }

    public static Optional<AssessmentSkillCatalog> find(String code) {
        return Arrays.stream(values()).filter(skill -> skill.name().equals(code)).findFirst();
    }
}
