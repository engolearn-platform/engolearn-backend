package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentSkillCatalog;
import fit.iuh.engolearn.assessment.contract.MicroSkillCatalog;
import fit.iuh.engolearn.assessment.dto.request.MicroSkillRequest;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.model.MicroSkill;
import fit.iuh.engolearn.assessment.repository.MicroSkillRepository;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicroSkillService implements MicroSkillCatalog {
    static final String PUBLICATION_GATE = "micro-skill-catalog";

    private final MicroSkillRepository skills;
    private final AssessmentTransactions transactions;

    @Override
    public List<Entry> list() { return skills.list().stream().map(MicroSkillService::entry).toList(); }

    @Override
    public Optional<Entry> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(skills.find(code)).map(MicroSkillService::entry);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Entry create(MicroSkillRequest.Create request) {
        String section = request.section().name();
        if (request.code().charAt(0) != section.charAt(0)) {
            throw new AssessmentException(422, "MICRO_SKILL_SECTION", "Mã R dành cho Đọc, mã L dành cho Nghe.");
        }
        var mapping = AssessmentSkillCatalog.find(request.code());
        var skill = MicroSkill.builder().id(request.code()).label(request.label().strip()).section(section)
                .active(mapping.isPresent()).legacyName(mapping.map(AssessmentSkillCatalog::learningPathName).orElse(null))
                .build();
        try {
            return entry(skills.insert(skill));
        } catch (DuplicateKeyException conflict) {
            throw new AssessmentException(409, "MICRO_SKILL_EXISTS", "Mã kỹ năng đã tồn tại.");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Entry rename(String code, String label) {
        return updated(code, new Update().set("label", label.strip()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Entry retire(String code) {
        return transactions.execute(PUBLICATION_GATE, () -> updated(code, new Update().set("active", false)));
    }

    private Entry updated(String code, Update changes) {
        var skill = skills.update(code, changes);
        if (skill == null) throw new AssessmentException(404, "MICRO_SKILL_NOT_FOUND", "Không tìm thấy kỹ năng.");
        return entry(skill);
    }

    private static Entry entry(MicroSkill skill) {
        return new Entry(skill.getId(), skill.getLabel(), skill.getSection(), skill.isActive(), skill.getLegacyName());
    }
}
