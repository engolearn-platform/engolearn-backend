package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import fit.iuh.engolearn.speaking.model.enums.SpeakingCriterionStatus;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingCriterionVo implements EmbeddedDocument {
    @Field("status") private SpeakingCriterionStatus status;
    @Field("score") private Double score;
    @Field("reason") private String reason;
    @Field("sample_count") private Integer sampleCount;
    @Field("word_count") private Integer wordCount;
    @Field("speech_duration_ms") private Long speechDurationMs;
    @Field("accuracy") private SpeakingRubricComponentVo accuracy;
    @Field("range") private SpeakingRubricComponentVo range;
    @Field("examples") private List<SpeakingRubricExampleVo> examples;
}
