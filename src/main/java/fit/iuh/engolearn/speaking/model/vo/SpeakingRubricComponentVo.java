package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import fit.iuh.engolearn.speaking.model.enums.SpeakingCriterionStatus;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingRubricComponentVo implements EmbeddedDocument {
    @Field("status") private SpeakingCriterionStatus status;
    @Field("level") private Integer level;
    @Field("reason_vi") private String reasonVi;
}
