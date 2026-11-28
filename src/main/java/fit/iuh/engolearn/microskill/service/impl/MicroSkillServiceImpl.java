package fit.iuh.engolearn.microskill.service.impl;

import fit.iuh.engolearn.microskill.service.MicroSkillService;
import fit.iuh.engolearn.microskill.repository.MicroSkillRepository;
import fit.iuh.engolearn.microskill.model.MicroSkill;
import fit.iuh.engolearn.microskill.mapper.MicroSkillMapper;
import fit.iuh.engolearn.microskill.dto.request.*;
import fit.iuh.engolearn.microskill.dto.response.*;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MicroSkillServiceImpl implements MicroSkillService {
    private final MicroSkillRepository repository;
    private final CurrentUserProvider users;
    @PreAuthorize("hasRole('USER')")
    public MicroSkillListResponse list() {
        return new MicroSkillListResponse(repository.list().stream().map(MicroSkillMapper::toResponse).toList());
    }
    @PreAuthorize("hasRole('USER')")
    public MicroSkillResponse detail(String id) { return MicroSkillMapper.toResponse(require(id)); }
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public MicroSkillResponse create(CreateMicroSkillRequest request) {
        String actor = users.requireCurrentUserId();
        var value = MicroSkill.builder().id("MS-" + UUID.randomUUID()).code(request.code())
                .canonicalName(request.canonicalName().strip()).displayNameVi(request.displayNameVi().strip())
                .descriptionVi(request.descriptionVi().strip()).skill(request.skill()).status(request.status())
                .displayOrder(request.displayOrder()).revision(1).referenced(false)
                .createdBy(actor).updatedBy(actor).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        return MicroSkillMapper.toResponse(repository.insert(value));
    }
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public MicroSkillResponse update(String id, UpdateMicroSkillRequest request) {
        var value = require(id);
        value.setDisplayNameVi(request.displayNameVi().strip());
        value.setDescriptionVi(request.descriptionVi().strip());
        value.setStatus(request.status());
        value.setDisplayOrder(request.displayOrder());
        value.setUpdatedBy(users.requireCurrentUserId());
        return MicroSkillMapper.toResponse(repository.update(value, request.revision()).orElseThrow(() -> conflict("Catalog revision changed")));
    }
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public DeleteMicroSkillResponse delete(String id, int revision) {
        users.requireCurrentUserId();
        require(id);
        if (!repository.deleteUnused(id, revision)) throw conflict("Definition is referenced or revision changed; deactivate referenced definitions instead");
        return new DeleteMicroSkillResponse(id, true);
    }
    private MicroSkill require(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Micro-skill not found"));
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
