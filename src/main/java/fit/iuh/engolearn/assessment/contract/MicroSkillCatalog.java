package fit.iuh.engolearn.assessment.contract;

import java.util.List;
import java.util.Optional;

public interface MicroSkillCatalog {
    record Entry(String code, String label, String section, boolean active, String legacyName) {
        public boolean availableForAssessment() {
            return active && AssessmentSkillCatalog.find(code)
                    .filter(known -> known.learningPathName().equals(legacyName) && known.section().equals(section))
                    .isPresent();
        }
    }

    List<Entry> list();
    Optional<Entry> find(String code);
}
