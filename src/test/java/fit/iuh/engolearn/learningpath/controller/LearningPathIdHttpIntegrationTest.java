package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
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
    void closedDayResultReturnsReviewOverHttp() {
        ObjectId id = new ObjectId();
        ObjectId userId = new ObjectId();
        List<Document> items = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("order", i)
                .append("question_id", "review-" + i).append("micro_skill", "Reading for Gist")).toList();
        Document day = new Document("day_no", 1).append("status", "CLOSED")
                .append("progress", new Document("assigned", 10).append("answered", 0).append("correct", 0))
                .append("result", new Document("wrong", 0).append("completion_rate", 0.0))
                .append("items", items);
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", id).append("user_id", userId)
                .append("status", "COMPLETED").append("is_active", false)
                .append("weeks", List.of(new Document("week_no", 1).append("days", List.of(day)))));
        List<Document> options = List.of(new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"),
                new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        List<Document> snapshots = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("path_id", id)
                .append("user_id", userId).append("question_id", "review-" + i)
                .append("micro_skill", "Reading for Gist").append("skill", "READING")
                .append("prompt", "Question " + i).append("options", options)
                .append("correct_option_id", "A").append("explanation", "Explanation")).toList();
        mongoTemplate.getCollection("learning_path_questions").insertMany(snapshots);
        String url = "http://localhost:" + port + "/api/learning-paths/" + id.toHexString()
                + "/weeks/1/days/1/result?userId=" + userId.toHexString();
        JsonNode data = JsonMapper.builder().build().readTree(restTemplate.getForObject(url, String.class)).path("data");
        assertThat(data.path("answered").asInt()).isZero();
        assertThat(data.path("practiceScore").isNull()).isTrue();
        assertThat(data.path("questions").size()).isEqualTo(10);
        assertThat(data.path("questions").get(0).path("answerStatus").asString()).isEqualTo("UNANSWERED");
        assertThat(data.path("questions").get(0).path("correctOptionId").asString()).isEqualTo("A");
    }

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
