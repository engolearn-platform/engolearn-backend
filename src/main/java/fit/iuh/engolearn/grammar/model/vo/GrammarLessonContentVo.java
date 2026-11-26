package fit.iuh.engolearn.grammar.model.vo;

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
public class GrammarLessonContentVo implements EmbeddedDocument {
    @Field("title_vi")
    private String titleVi;
    @Field("title_en")
    private String titleEn;
    @Field("objective")
    private String objective;
    @Field("tags")
    private List<String> tags;
    @Field("blocks")
    private List<GrammarBlockVo> blocks;
    @Field("questions")
    private List<GrammarQuestionVo> questions;
}
