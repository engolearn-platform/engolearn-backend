package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class ResetReceiptVo implements EmbeddedDocument {
    @Field("key")
    private String key;

    @Field("fingerprint")
    private String fingerprint;

    @Field("previous_generation")
    private long previousGeneration;

    @Field("generation")
    private long generation;
}
