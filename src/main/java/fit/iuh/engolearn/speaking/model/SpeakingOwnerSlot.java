package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.vo.SpeakingStartVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("speaking_v2_owner_slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SpeakingOwnerSlot extends BaseDocument {
    @Version @Field("revision") private Long revision;
    @Field("start") private SpeakingStartVo start;
}
