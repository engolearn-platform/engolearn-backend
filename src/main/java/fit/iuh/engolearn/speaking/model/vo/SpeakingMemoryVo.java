package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingMemoryVo implements EmbeddedDocument {
    public enum Operation { UPSERT, REMOVE }
    public enum Target { FACT, COVERED_TOPIC, OPEN_THREAD }
    public record Delta(Operation operation, Target target, String key, String value) implements EmbeddedDocument {}

    @Field("facts") private Map<String, String> facts = new LinkedHashMap<>();
    @Field("covered_topics") private Set<String> coveredTopics = new LinkedHashSet<>();
    @Field("open_threads") private Set<String> openThreads = new LinkedHashSet<>();
    @Field("version") private int version;
    @Field("processed_through_turn") private int processedThroughTurn;
}
