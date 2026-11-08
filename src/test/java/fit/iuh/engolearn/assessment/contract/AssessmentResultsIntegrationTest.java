package fit.iuh.engolearn.assessment.contract;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
class AssessmentResultsIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired private AssessmentResults results;

    @Test
    void readsTheRequestedOlderResultWithAllSkillsAndWithoutWritingOrDisplayGrants() {
        String newestId = new ObjectId().toHexString();
        mongoTemplate.getCollection("assessment_attempts").insertMany(List.of(
                attempt("native-old", "learner", "READY", false, "2026-10-01T00:00:00Z"),
                attempt(newestId, "learner", "READY", false, "2026-11-01T00:00:00Z"),
                attempt("foreign", "another", "READY", false, "2026-11-01T00:00:00Z"),
                attempt("demo", "learner", "READY", true, "2026-11-01T00:00:00Z"),
                attempt("active", "learner", "ACTIVE", false, "2026-11-01T00:00:00Z"),
                attempt("no-result", "learner", "NO_RESULT", false, "2026-11-01T00:00:00Z"),
                attempt("abandoned", "learner", "ABANDONED", false, "2026-11-01T00:00:00Z")));
        var before = mongoTemplate.getCollection("assessment_attempts").find().into(new ArrayList<>());

        var old = results.findCompleted("learner", "native-old").orElseThrow();
        assertThat(old.attemptId()).isEqualTo("native-old");
        assertThat(old.skills()).hasSize(5);
        assertThat(old.submittedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(results.latest("learner").orElseThrow().attemptId()).isEqualTo(newestId);
        for (String id : List.of("missing", "foreign", "demo", "active", "no-result", "abandoned")) {
            assertThat(results.findCompleted("learner", id)).as(id).isEmpty();
        }
        assertThat(results.findCompletedAttemptIds("learner", Set.of("native-old", newestId, "missing", "foreign", "demo", "active", "no-result", "abandoned")))
                .containsExactlyInAnyOrder("native-old", newestId);
        assertThat(results.findCompletedAttemptIds("learner", Set.of())).isEmpty();
        assertThat(mongoTemplate.getCollection("assessment_attempts").find().into(new ArrayList<>())).isEqualTo(before);
        assertThat(mongoTemplate.collectionExists("assessment_gates")).isFalse();
        assertThat(mongoTemplate.collectionExists("attempts")).isFalse();
        assertThat(mongoTemplate.collectionExists("micro_skill_results")).isFalse();
    }

    private Document attempt(String id, String owner, String status, boolean demo, String submittedAt) {
        return new Document("_id", ObjectId.isValid(id) ? new ObjectId(id) : id).append("user_id", owner)
                .append("status", status).append("level", "B1").append("submitted_at", Date.from(Instant.parse(submittedAt)))
                .append("paper", new Document("paper_id", "native-paper").append("demonstration", demo))
                .append("analysis_grants", List.of()).append("skill_results", List.of("R01", "R02", "R04", "L01", "L03").stream()
                        .map(code -> new Document("code", code).append("correct", 2).append("total", 10)
                                .append("percentage", 20).append("band", "NEEDS_PRACTICE")).toList());
    }
}
