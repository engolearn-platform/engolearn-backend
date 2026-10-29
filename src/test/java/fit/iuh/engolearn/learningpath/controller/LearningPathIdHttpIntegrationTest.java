package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
class LearningPathIdHttpIntegrationTest extends AbstractMongoIntegrationTest {

    @Test
    void existingObjectIdIsReturnedUnchangedByListSearchAndDetail() {
        ObjectId id = new ObjectId();
        ObjectId userId = new ObjectId();
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", id)
                .append("user_id", userId)
                .append("created_at", Date.from(Instant.parse("2026-10-27T03:00:00Z")))
                .append("created_date", "2026-10-27")
                .append("status", "NOT_STARTED")
                .append("is_active", false)
                .append("level", "A1")
                .append("k", 1));
        String base = "http://localhost:" + port + "/api/learning-paths";
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode list = mapper.readTree(restTemplate.getForObject(
                base + "?userId=" + userId.toHexString() + "&status=ACTIVE", String.class));
        JsonNode search = mapper.readTree(restTemplate.getForObject(
                base + "/search?userId=" + userId.toHexString() + "&keyword=27/10/2026", String.class));
        JsonNode detail = mapper.readTree(restTemplate.getForObject(
                base + "/" + id.toHexString() + "?userId=" + userId.toHexString(), String.class));

        assertThat(list.path("data").path("items").get(0).path("id").asString()).isEqualTo(id.toHexString());
        assertThat(search.path("data").path("items").get(0).path("id").asString()).isEqualTo(id.toHexString());
        assertThat(detail.path("data").path("id").asString()).isEqualTo(id.toHexString());
        assertThat(mongoTemplate.getCollection("learning_paths").find(new Document("_id", id)).first()
                .get("_id")).isEqualTo(id);
    }
}
