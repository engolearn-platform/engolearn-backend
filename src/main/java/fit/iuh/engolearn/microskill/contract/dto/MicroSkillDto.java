package fit.iuh.engolearn.microskill.contract.dto;

public record MicroSkillDto(String id, String code, String canonicalName, String displayNameVi,
                            String descriptionVi, String skill, boolean active, int displayOrder) {}
