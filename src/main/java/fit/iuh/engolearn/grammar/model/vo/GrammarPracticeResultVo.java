package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
public class GrammarPracticeResultVo implements EmbeddedDocument {
    @Field("score")
    private int score;
    @Field("passed")
    private boolean passed;
    @Field("completed")
    private boolean completed;
    @Field("topic_completed")
    private boolean topicCompleted;
    @Field("archived")
    private boolean archived;
    @Field("newly_unlocked_levels")
    private List<GrammarLevel> newlyUnlockedLevels;
}
