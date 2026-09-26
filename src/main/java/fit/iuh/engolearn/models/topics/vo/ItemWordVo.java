package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemWordVo implements EmbeddedDocument {
    @Field("vocab_id")
    private String vocabId;

    @Field("sense_id")
    private String senseId;

    @Field("snapshot")
    private WordSnapshotVo snapshot;

    @Field("samples")
    private List<WordSampleVo> samples;

    @Field("collocations")
    private List<CollocationVo> collocations;

    @Field("quick_check")
    private QuickCheckVo quickCheck;

    @Field("order_index")
    private Integer orderIndex;

    @Field("tips")
    private String tips;
}
