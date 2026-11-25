package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarUnlockReason;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_level_unlocks")
@CompoundIndex(name = "grammar_learner_level", def = "{'user_id': 1, 'level': 1}", unique = true)
public class GrammarLevelUnlock extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("level")
    private GrammarLevel level;
    @Field("unlocked_at")
    private Instant unlockedAt;
    @Field("reason")
    private GrammarUnlockReason reason;
}
