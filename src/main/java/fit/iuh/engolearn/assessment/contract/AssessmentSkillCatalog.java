package fit.iuh.engolearn.assessment.contract;

import java.util.Arrays;
import java.util.Optional;

/** Skill codes with an agreed Assessment to Learning Path mapping. */
public enum AssessmentSkillCatalog {
    R01("READING", "Đọc tìm thông tin cụ thể", "Reading for Specific Information"),
    R02("READING", "Đọc tìm ý chính", "Reading for Gist"),
    R04("READING", "Đọc suy luận và quan điểm tác giả", "Inference & Author's View"),
    L01("LISTENING", "Nghe ý chính", "Listening for Gist"),
    L03("LISTENING", "Nghe tìm thông tin cụ thể", "Listening for Specific Information");

    private final String section;
    private final String label;
    private final String learningPathName;

    AssessmentSkillCatalog(String section, String label, String learningPathName) {
        this.section = section;
        this.label = label;
        this.learningPathName = learningPathName;
    }

    public String section() { return section; }
    public String label() { return label; }
    public String learningPathName() { return learningPathName; }

    public static Optional<AssessmentSkillCatalog> find(String code) {
        return Arrays.stream(values()).filter(skill -> skill.name().equals(code)).findFirst();
    }
}
