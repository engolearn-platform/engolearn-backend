package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarAuditStateVo implements EmbeddedDocument {
    @Field("status")
    private GrammarStatus status;
    @Field("order")
    private Integer order;
    @Field("title_vi")
    private String titleVi;
    @Field("title_en")
    private String titleEn;
    @Field("description")
    private String description;
    @Field("level")
    private GrammarLevel level;
    @Field("content_version")
    private Integer contentVersion;
    @Field("ready")
    private Boolean ready;
}
