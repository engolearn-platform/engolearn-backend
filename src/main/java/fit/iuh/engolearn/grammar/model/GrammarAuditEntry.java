package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarAuditAction;
import fit.iuh.engolearn.grammar.model.vo.GrammarAuditStateVo;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_audit_log")
@CompoundIndex(name = "grammar_audit_topic_time", def = "{'topic_id': 1, 'created_at': -1}")
public class GrammarAuditEntry extends BaseDocument {
    @Field("actor_id")
    private String actorId;
    @Field("topic_id")
    private String topicId;
    @Field("lesson_id")
    private String lessonId;
    @Field("action")
    private GrammarAuditAction action;
    @Field("before")
    private GrammarAuditStateVo before;
    @Field("after")
    private GrammarAuditStateVo after;
}
