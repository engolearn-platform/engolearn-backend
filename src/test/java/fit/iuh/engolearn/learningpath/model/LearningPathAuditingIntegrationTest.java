package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
class LearningPathAuditingIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private LearningPathRepository paths;

    @Test
    void savingAssignedIdKeepsCreationTimeAndObjectIdUnderMongoAuditing() {
        ObjectId id = new ObjectId();
        Instant createdAt = Instant.parse("2026-10-27T03:00:00Z");
        LearningPath path = new LearningPath();
        path.setId(id.toHexString());
        path.setCreatedAt(createdAt);
        path.setUserId(new ObjectId());

        paths.save(path);

        Document stored = mongoTemplate.getCollection("learning_paths").find(new Document("_id", id)).first();
        assertThat(stored).isNotNull();
        assertThat(stored.get("_id")).isEqualTo(id);
        assertThat(stored.getDate("created_at")).isEqualTo(Date.from(createdAt));
        assertThat(stored.getDate("updated_at")).isNotNull();
        assertThat(paths.findById(id.toHexString())).get()
                .extracting(LearningPath::getCreatedAt).isEqualTo(createdAt);
    }
}
