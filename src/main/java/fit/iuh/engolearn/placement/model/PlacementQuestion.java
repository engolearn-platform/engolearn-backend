package fit.iuh.engolearn.placement.model;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionOptionVo;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionProvenanceVo;
import fit.iuh.engolearn.shared.BaseDocument;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("placement_questions")
@CompoundIndex(name = "placement_question_assembly",
        def = "{'status': 1, 'skill': 1, 'level': 1, '_id': 1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PlacementQuestion extends BaseDocument {
    @Field("code") private String code;
    @Field("skill") private PlacementSkill skill;
    @Field("level") private PlacementLevel level;
    @Field("prompt") private String prompt;
    @Field("passage") private String passage;
    @Field("options") private List<PlacementQuestionOptionVo> options;
    @Field("correct_option_id") private String correctOptionId;
    @Field("explanation") private String explanation;
    @Field("audio_url") private String audioUrl;
    @Field("transcript") private String transcript;
    @Field("status") private PlacementQuestionStatus status;
    @Field("provenance") private PlacementQuestionProvenanceVo provenance;
}
