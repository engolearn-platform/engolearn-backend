package fit.iuh.engolearn.microskill.dto.response;
import fit.iuh.engolearn.microskill.model.enums.*;
import java.time.Instant;
public record MicroSkillResponse(String id, String code, String canonicalName, String displayNameVi,
        String descriptionVi, MicroSkillCategory skill, MicroSkillStatus status, int displayOrder,
        int revision, boolean referenced, Instant createdAt, Instant updatedAt) {}
