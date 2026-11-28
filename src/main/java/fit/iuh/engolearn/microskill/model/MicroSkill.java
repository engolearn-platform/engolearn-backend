package fit.iuh.engolearn.microskill.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.microskill.model.enums.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

@Document("micro_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MicroSkill extends BaseDocument {
    @Indexed(name = "code", unique = true)
    @Field("code")
    private String code;

    @Indexed(name = "canonical_name", unique = true)
    @Field("canonical_name")
    private String canonicalName;

    @Field("display_name_vi")
    private String displayNameVi;

    @Field("description_vi")
    private String descriptionVi;

    @Field("skill")
    private MicroSkillCategory skill;

    @Field("status")
    private MicroSkillStatus status;

    @Field("display_order")
    private int displayOrder;

    @Field("revision")
    private int revision;

    @Field("referenced")
    private boolean referenced;

    @Field("created_by")
    private String createdBy;

    @Field("updated_by")
    private String updatedBy;
}
