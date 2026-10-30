package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class ContentMutationReceiptVo implements EmbeddedDocument {
    @Field("key")
    private String key;

    @Field("fingerprint")
    private String fingerprint;

    @Field("stage")
    private LearningStage stage;

    @Field("item_index")
    private int itemIndex;

    @Field("revision")
    private long revision;

    @Field("accessed_at")
    private Instant accessedAt;

    @Field("rounded_progress")
    private Boolean roundedProgress;
}
