package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class GrammarExampleVo implements EmbeddedDocument {
    @Field("english")
    private String english;
    @Field("vietnamese")
    private String vietnamese;
    @Field("analysis")
    private String analysis;
}
