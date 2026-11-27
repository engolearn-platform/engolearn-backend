package fit.iuh.engolearn.assessment.contract;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionHistoryQueryDto;
import fit.iuh.engolearn.assessment.contract.exception.AssessmentHistoryInvalidQueryException;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.assessment.service.AssessmentAccess;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import fit.iuh.engolearn.assessment.service.impl.AssessmentContractsServiceImpl;
import fit.iuh.engolearn.assessment.utils.AssessmentHistoryCursorCodec;
import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
class AssessmentCompanionHistoryIntegrationTest extends AbstractMongoIntegrationTest {
    private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");
    @Autowired private AssessmentRepository repository;

    @Test
    void ownerReadyProjectionAndMixedIdTiesArePagedWithoutWrites() {
        var collection = mongoTemplate.getCollection("assessment_attempts");
        var high = new ObjectId("000000000000000000000002");
        var low = new ObjectId("000000000000000000000001");
        collection.insertMany(List.of(
                attempt(high, "owner", "READY", false, AT),
                attempt(low, "owner", "READY", false, AT),
                attempt("z", "owner", "READY", false, AT),
                attempt("a", "owner", "READY", false, AT),
                attempt("old", "owner", "READY", false, AT.minusSeconds(1)),
                attempt("foreign", "another", "READY", false, AT),
                attempt("demo", "owner", "READY", true, AT),
                attempt("active", "owner", "ACTIVE", false, AT),
                attempt("no-result", "owner", "NO_RESULT", false, AT),
                attempt("missing-time", "owner", "READY", false, null)));
        var before = collection.find().into(new ArrayList<>());
        var access = mock(AssessmentAccess.class);
        var transactions = mock(AssessmentTransactions.class);
        var service = new AssessmentContractsServiceImpl(repository, transactions, new MockEnvironment(), access);

        var page1 = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, null));
        assertThat(page1.items()).extracting(row -> row.result().attemptId()).containsExactly(high.toHexString(), low.toHexString());
        assertThat(page1.hasMore()).isTrue();
        var page2 = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, page1.nextCursor()));
        assertThat(page2.items()).extracting(row -> row.result().attemptId()).containsExactly("z", "a");
        assertThat(page2.hasMore()).isTrue();
        var page3 = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, page2.nextCursor()));
        assertThat(page3.items()).extracting(row -> row.result().attemptId()).containsExactly("old");
        assertThat(page3.hasMore()).isFalse();
        assertThat(page3.nextCursor()).isNull();
        assertThat(page1.items().getFirst().result().skills()).extracting("code").containsExactly("R01", "R02");
        assertThat(page1.items().getFirst().paperVersion()).isEqualTo(2);
        assertThat(page1.items().getFirst().rulesVersion()).isEqualTo(3);

        for (String id : List.of("foreign", "demo", "active", "no-result", "missing-time", "missing")) {
            String cursor = AssessmentHistoryCursorCodec.encode("owner", false, 2,
                    new AssessmentHistoryCursorCodec.Position(AT, id));
            assertThatThrownBy(() -> service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, cursor)))
                    .as(id).isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        }
        var projected = repository.companionHistory("owner", false, null, null, 3);
        assertThat(projected).hasSize(3);
        assertThat(projected.getFirst().getAnswers()).isEmpty();
        assertThat(projected.getFirst().getPaper().getUnits()).isNull();
        assertThat(collection.find().into(new ArrayList<>())).isEqualTo(before);
        assertThat(mongoTemplate.collectionExists("assessment_gates")).isFalse();
        assertThat(mongoTemplate.collectionExists("assessment_grant_revocations")).isFalse();
        verifyNoInteractions(transactions);
    }

    @Test
    void existingHistoryIndexSupportsLimitedOwnerQueryOnSyntheticHistory() {
        var collection = mongoTemplate.getCollection("assessment_attempts");
        collection.createIndex(new Document("user_id", 1).append("submitted_at", -1).append("_id", -1),
                new com.mongodb.client.model.IndexOptions().name("assessment_history"));
        var fixtures = new ArrayList<Document>();
        for (int i = 0; i < 600; i++) {
            fixtures.add(attempt("foreign-" + i, "other", "READY", false, AT.minusSeconds(i)));
        }
        for (int i = 0; i < 120; i++) {
            fixtures.add(attempt("owner-" + i, "owner", "READY", false, AT.minusSeconds(i)));
        }
        collection.insertMany(fixtures);
        var filter = new Document("user_id", "owner").append("status", "READY")
                .append("submitted_at", new Document("$ne", null)).append("paper.demonstration", new Document("$ne", true));
        var projection = new Document("_id", 1).append("submitted_at", 1).append("level", 1)
                .append("correct_count", 1).append("skill_results", 1).append("analysis_grants", 1)
                .append("paper.paper_id", 1).append("paper.version", 1).append("paper.rules_version", 1)
                .append("paper.demonstration", 1);
        var explain = collection.find(filter).projection(projection)
                .sort(new Document("submitted_at", -1).append("_id", -1)).limit(3)
                .explain(com.mongodb.ExplainVerbosity.EXECUTION_STATS);
        var stats = explain.get("executionStats", Document.class);
        assertThat(explain.get("queryPlanner", Document.class).get("winningPlan").toString()).contains("assessment_history");
        assertThat(stats.getInteger("nReturned")).isEqualTo(3);
        assertThat(stats.getInteger("totalDocsExamined")).isLessThanOrEqualTo(6);
        assertThat(repository.companionHistory("owner", false, null, null, 3))
                .extracting(row -> row.getId()).containsExactly("owner-0", "owner-1", "owner-2");
        System.out.printf("Synthetic history explain: docs=%s keys=%s millis=%s%n",
                stats.get("totalDocsExamined"), stats.get("totalKeysExamined"), stats.get("executionTimeMillis"));
    }

    private Document attempt(Object id, String owner, String status, boolean demo, Instant at) {
        return new Document("_id", id).append("user_id", owner).append("status", status).append("level", "B1")
                .append("submitted_at", at == null ? null : Date.from(at)).append("correct_count", 32)
                .append("paper", new Document("paper_id", "paper").append("version", 2).append("rules_version", 3)
                        .append("demonstration", demo).append("units", List.of(new Document("canary", "hidden"))))
                .append("answers", new Document("answer", 2)).append("analysis_grants", List.of())
                .append("skill_results", List.of("R01", "R02", "R03", "L01", "L02").stream()
                        .map(code -> new Document("code", code).append("section", code.startsWith("R") ? "READING" : "LISTENING")
                                .append("correct", 2).append("total", 10).append("percentage", 20)).toList());
    }
}