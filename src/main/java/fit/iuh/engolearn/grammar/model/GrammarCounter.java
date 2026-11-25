package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_counters")
public class GrammarCounter extends BaseDocument {
    @Field("topic_sequence")
    private long topicSequence;
    @Field("ordering_revision")
    private long orderingRevision;
}
