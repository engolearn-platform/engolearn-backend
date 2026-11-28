package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentSkillCatalog;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.service.AssessmentMicroSkillCatalog;
import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AssessmentMicroSkills implements AssessmentMicroSkillCatalog {
    private final MicroSkillLookup catalog;

    @Override
    public List<Entry> list() {
        var definitions = catalog.list().stream()
                .collect(Collectors.toMap(MicroSkillDto::canonicalName, Function.identity()));
        return Arrays.stream(AssessmentSkillCatalog.values())
                .flatMap(mapping -> entry(mapping, definitions.get(mapping.learningPathName())).stream())
                .toList();
    }

    @Override
    public Entry retainActive(String code) {
        return AssessmentSkillCatalog.find(code)
                .flatMap(mapping -> catalog.retainActive(mapping.learningPathName()).flatMap(skill -> entry(mapping, skill)))
                .orElseThrow(() -> new AssessmentException(422, "MICRO_SKILL_UNAVAILABLE",
                        "Kỹ năng " + code + " chưa có trong danh mục chung, đã ngừng dùng hoặc sai nhóm."));
    }

    private static Optional<Entry> entry(AssessmentSkillCatalog mapping, MicroSkillDto skill) {
        if (skill == null || !mapping.section().equals(skill.skill())
                || skill.displayNameVi() == null || skill.displayNameVi().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Entry(mapping.name(), skill.displayNameVi(), mapping.section(), skill.active()));
    }
}
