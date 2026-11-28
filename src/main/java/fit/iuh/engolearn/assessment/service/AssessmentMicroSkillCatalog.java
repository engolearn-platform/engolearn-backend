package fit.iuh.engolearn.assessment.service;

import java.util.List;

public interface AssessmentMicroSkillCatalog {
    record Entry(String code, String label, String section, boolean active) {}

    List<Entry> list();

    Entry retainActive(String code);
}
