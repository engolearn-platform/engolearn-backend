package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("topic_learner_profiles")
public record TopicLearnerProfile(@Id String id, String displayName, CefrLevel cefrLevel, Instant createdAt) {}
