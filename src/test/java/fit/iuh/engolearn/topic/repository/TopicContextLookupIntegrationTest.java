package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.topic.contract.TopicContextLookup;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningContextDto;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningPositionDto;
import fit.iuh.engolearn.topic.contract.enums.TopicContextStage;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.service.TopicGenerationService;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
class TopicContextLookupIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired TopicContextLookup lookup;
    @Autowired TopicGenerationService generations;
    @Autowired ObjectMapper mapper;

    @Test void publishedOverviewAndFocusedContentUsePinnedVersionWithoutRestrictedData() {
        Fixture f = seed();
        var overview = lookup.getOverview("owner", f.topic()).orElseThrow();
        assertThat(overview.items()).extracting(item -> item.title()).containsExactly("Airport v2");
        var result = lookup.getLearningContext("owner", f.attempt(), pos(TopicContextStage.VOCAB, 0)).orElseThrow();
        assertThat(result.topicItem().title()).isEqualTo("Airport v1");
        assertThat(result.content()).isInstanceOf(TopicLearningContextDto.VocabularyDto.class);
        var word = (TopicLearningContextDto.VocabularyDto) result.content();
        assertThat(word.word()).isEqualTo("departure gate");
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("Travel", "checking in", "departure gate")
                .doesNotContain("hidden-answer-sentinel", "other-word", "Airport v2", "quickCheck", "quizzes");
        var dialogue = lookup.getLearningContext("owner", f.attempt(), pos(TopicContextStage.CONTEXT, 0)).orElseThrow();
        assertThat(mapper.writeValueAsString(dialogue)).contains("May I see your passport?").doesNotContain("departure gate");
        assertThat(lookup.getLearningContext("owner", f.attempt(), pos(TopicContextStage.EXPRESSIONS, 0))).isEmpty();
        UserTopicItemAttempt attempt = mongoTemplate.findById(f.attempt(), UserTopicItemAttempt.class);
        attempt.setFurthestReached(FurthestReachedVo.builder().stage(LearningStage.EXPRESSIONS).itemIndex(0).build());
        mongoTemplate.save(attempt);
        assertThat(mapper.writeValueAsString(lookup.getLearningContext("owner", f.attempt(),
                pos(TopicContextStage.EXPRESSIONS, 0)).orElseThrow())).contains("Could you tell me").doesNotContain("other-word");
        assertThat(lookup.getLearningContext("owner", f.attempt(), pos(TopicContextStage.VOCAB, 9))).isEmpty();
    }

    @Test void ownershipResetAndPublicationAreRevalidated() {
        Fixture f = seed();
        var position = pos(TopicContextStage.CONTEXT, 0);
        assertThat(lookup.getLearningContext("foreign", f.attempt(), position)).isEmpty();
        mongoTemplate.insert(TopicLearnerState.builder().id(generations.id("owner", f.topic()))
                .userId("owner").topicId(f.topic()).generation(1).build());
        assertThat(lookup.getLearningContext("owner", f.attempt(), position)).isEmpty();
        var attempt = mongoTemplate.findById(f.attempt(), UserTopicItemAttempt.class);
        attempt.setGeneration(1);
        mongoTemplate.save(attempt);
        var current = mongoTemplate.findById(f.current(), TopicItemVersion.class);
        current.setStatus(ContentStatus.HIDDEN);
        mongoTemplate.save(current);
        assertThat(lookup.getLearningContext("owner", f.attempt(), position)).isEmpty();
        assertThat(lookup.getOverview("owner", f.topic()).orElseThrow().items()).isEmpty();
        var release = mongoTemplate.findById(f.release(), TopicVersion.class);
        release.setStatus(ContentStatus.DRAFT);
        mongoTemplate.save(release);
        assertThat(lookup.getOverview("owner", f.topic())).isEmpty();
    }

    @Test void readPathUsesBoundedFindsAndNoLookupAggregation() {
        Fixture f = seed();
        mongoTemplate.getDb().runCommand(new Document("profile", 2));
        try {
            var result = lookup.getLearningContext("owner", f.attempt(), pos(TopicContextStage.VOCAB, 0)).orElseThrow();
            var findings = mongoTemplate.getDb().getCollection("system.profile").find(
                    new Document("command.find", new Document("$in", List.of("topics", "topic_versions",
                            "topic_item_versions", "user_topic_item_attempts", "topic_learner_state")))).into(new java.util.ArrayList<>());
            assertThat(findings).hasSize(6);
            assertThat(mongoTemplate.getDb().getCollection("system.profile").countDocuments(
                    new Document("command.aggregate", new Document("$in", List.of("topics", "topic_versions", "topic_item_versions"))))).isZero();
            System.out.println("M4 learning-position Topic find count=" + findings.size() + ", serialized bytes="
                    + mapper.writeValueAsString(result).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        } finally {
            mongoTemplate.getDb().runCommand(new Document("profile", 0));
        }
    }

    private TopicLearningPositionDto pos(TopicContextStage stage, int index) { return new TopicLearningPositionDto(stage, index); }
    private String id() { return new ObjectId().toHexString(); }
    private Fixture seed() {
        String topic = id(), release = id(), item = id(), pinned = id(), current = id(), hidden = id(), attempt = id();
        mongoTemplate.insert(Topic.builder().id(topic).latestPublishedVersion(release).titleEn("Travel").titleVi("Du lich").description("Travel overview").build());
        mongoTemplate.insert(TopicVersion.builder().id(release).topicId(topic).status(ContentStatus.PUBLISHED).itemVersionIds(List.of(current, hidden)).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(current).topicId(topic).topicItemId(item).status(ContentStatus.PUBLISHED).title("Airport v2").order(0).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(hidden).topicId(topic).topicItemId(id()).status(ContentStatus.HIDDEN).title("Hidden lesson").order(1).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(pinned).topicId(topic).topicItemId(item).status(ContentStatus.PUBLISHED)
                .title("Airport v1").description("Airport lesson").version(1)
                .context(TopicContextVo.builder().scenarioDescription("checking in").dialogue(List.of(
                        DialogueTurnVo.builder().speaker("Staff").textEn("May I see your passport?").build())).build())
                .vocabularies(List.of(word("other-word", 2), word("departure gate", 0)))
                .expressions(List.of(ExpressionBlockVo.builder().purposes(List.of(
                        ExpressionPurposeVo.builder().purposeEn("Asking directions").orderIndex(0).examples(List.of(
                                ExpressionExampleVo.builder().textEn("Could you tell me where the gate is?").build())).build())).build()))
                .build());
        mongoTemplate.getCollection("topic_item_versions").updateOne(new Document("_id", new ObjectId(pinned)),
                new Document("$set", new Document("vocabularies.1.quick_check", new Document("answer_data", new Document("answer", "hidden-answer-sentinel")))
                        .append("quizzes", List.of(new Document("answer", "hidden-answer-sentinel")))));
        mongoTemplate.insert(UserTopicItemAttempt.builder().id(attempt).userId("owner").topicId(topic).topicItemId(item)
                .topicItemVersionId(pinned).status(AttemptStatus.IN_PROGRESS).attemptType(AttemptType.LEARNING)
                .furthestReached(FurthestReachedVo.builder().stage(LearningStage.VOCAB).itemIndex(0).build()).build());
        return new Fixture(topic, release, current, attempt);
    }
    private ItemWordVo word(String word, int order) {
        return ItemWordVo.builder().vocabId(word).orderIndex(order).snapshot(WordSnapshotVo.builder().word(word).pos("noun")
                .describeVi("Vietnamese meaning").build()).samples(List.of(WordSampleVo.builder().textEn("Example of " + word).build())).build();
    }
    private record Fixture(String topic, String release, String current, String attempt) {}
}
