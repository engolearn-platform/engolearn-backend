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

    @Indexed(unique = true)
    @Field("topic_item_version_id")
    private String topicItemVersionId;
    @Field("topic_item_id")
    private String topicItemId;
    @Field("goals")
    private List<SpeakingGoal> goals;
    @Field("goal_percentage")
    private int goalPercentage;
    @Field("minimum_valid_turns")
    private int minimumValidTurns;
    @Field("revision")
    private long revision;
    @Field("status")
    private Status status;

    public int requiredGoalCount() {
        return (goals.size() * goalPercentage + 99) / 100;
    }
}
