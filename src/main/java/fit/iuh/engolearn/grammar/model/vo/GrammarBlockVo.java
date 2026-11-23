package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.grammar.model.enums.GrammarBlockType;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class GrammarBlockVo implements EmbeddedDocument {
    @EqualsAndHashCode.Exclude
    @Field("block_id")
    private String id;
    @Field("type")
    private GrammarBlockType type;
    @Field("title")
    private String title;
    @Field("text")
    private String text;
    @Field("formula")
    private String formula;
    @Field("examples")
    private List<GrammarExampleVo> examples;
    @Field("rule_items")
    private List<String> ruleItems;
    @Field("incorrect")
    private String incorrect;
    @Field("correct")
    private String correct;
    @Field("explanation")
    private String explanation;
}
