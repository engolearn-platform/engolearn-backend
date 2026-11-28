package fit.iuh.engolearn.microskill.mapper;

import fit.iuh.engolearn.microskill.model.MicroSkill;
import fit.iuh.engolearn.microskill.model.enums.MicroSkillStatus;
import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import fit.iuh.engolearn.microskill.dto.response.MicroSkillResponse;

public final class MicroSkillMapper {
    private MicroSkillMapper() {}
    public static MicroSkillDto toDto(MicroSkill value) {
        return new MicroSkillDto(value.getId(), value.getCode(), value.getCanonicalName(), value.getDisplayNameVi(),
                value.getDescriptionVi(), value.getSkill().name(), value.getStatus() == MicroSkillStatus.ACTIVE, value.getDisplayOrder());
    }
    public static MicroSkillResponse toResponse(MicroSkill value) {
        return new MicroSkillResponse(value.getId(), value.getCode(), value.getCanonicalName(), value.getDisplayNameVi(),
                value.getDescriptionVi(), value.getSkill(), value.getStatus(), value.getDisplayOrder(), value.getRevision(),
                value.isReferenced(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
