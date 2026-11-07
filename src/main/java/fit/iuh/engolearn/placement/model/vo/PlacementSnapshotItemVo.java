package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementSnapshotItemVo implements EmbeddedDocument {
    @Field("id") private String id;
    @Field("order_index") private int order;
    @Field("skill") private PlacementSkill skill;
    @Field("level") private PlacementLevel level;
    @Field("label") private String label;
    @Field("prompt") private String prompt;
    @Field("passage") private String passage;
    @Field("options") private List<PlacementOptionVo> options;
    @Field("correct_option_id") private String correctOptionId;
    @Field("explanation") private String explanation;
    @Field("transcript") private String transcript;
    @Field("audio") private PlacementAudioVo audio;
}
