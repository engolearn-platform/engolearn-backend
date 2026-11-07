package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document("speaking_item_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingItemConfig extends BaseDocument {
    public enum Status { DRAFT, PUBLISHED }
    public enum OpeningSide { LEARNER, AI }

    @Indexed(unique = true)
    @Field("topic_item_version_id")
    private String topicItemVersionId;
    @Field("topic_item_id")
    private String topicItemId;
    @Field("goals")
    private List<SpeakingGoal> goals;
    @Field("context")
    private String context;
    @Field("learner_role")
    private String learnerRole;
    @Field("ai_role")
    private String aiRole;
    @Field("opening_side")
    private OpeningSide openingSide;
    @Field("goal_percentage")
    private Integer goalPercentage;
    @Field("minimum_valid_turns")
    private int minimumValidTurns;
    @Field("revision")
    private long revision;
    @Field("status")
    private Status status;

    public int requiredGoalCount() {
        if (goalPercentage == null) return (int) goals.stream()
                .filter(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED).count();
        return (goals.size() * goalPercentage + 99) / 100;
    }
}
