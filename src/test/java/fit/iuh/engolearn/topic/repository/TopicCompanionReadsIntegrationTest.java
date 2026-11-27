package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.contract.TopicCompanionReads;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.contract.exception.TopicCompanionInvalidQueryException;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.model.vo.CompletionResultVo;
import fit.iuh.engolearn.topic.service.TopicGenerationService;
import fit.iuh.engolearn.user.model.UserProfile;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@EnabledIfDockerAvailable
class TopicCompanionReadsIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired TopicCompanionReads reads;
    @Autowired TopicGenerationService generations;
    @Autowired ObjectMapper mapper;
    private static final String OWNER = "owner";
    private static final String FOREIGN = "foreign";
    private static final Instant AT = Instant.parse("2026-11-27T01:00:00Z");

    @Test void discoveryUsesLiteralKeywordLevelVisibilityAndOwnerBoundCursorWithoutWrites() {
        profiles();
        Fixture first = topic("Travel [one]", CefrLevel.B1, 1, ContentStatus.PUBLISHED);
        Fixture second = topic("Travel [two]", CefrLevel.B1, 2, ContentStatus.PUBLISHED);
        topic("Travel [hidden]", CefrLevel.B1, 3, ContentStatus.HIDDEN);
        topic("Travel [high]", CefrLevel.C1, 4, ContentStatus.PUBLISHED);
        topic("Travel other", CefrLevel.B1, 5, ContentStatus.PUBLISHED);
        mongoTemplate.insert(progress(FOREIGN, first, 0));
        var before = snapshot();
        var page = reads.discover(OWNER, new TopicDiscoveryQueryDto("[", null, 1, null));
        assertThat(page.items()).extracting(TopicDiscoveryPageDto.RowDto::topicId).containsExactly(first.topic());
        assertThat(page.items().getFirst().firstEligibleItem().itemId()).isEqualTo(first.item());
        assertThat(page.items().getFirst().visibleItemCount()).isEqualTo(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(reads.discover(OWNER, new TopicDiscoveryQueryDto("[", null, 1, page.nextCursor())).items())
                .extracting(TopicDiscoveryPageDto.RowDto::topicId).containsExactly(second.topic());
        assertThatThrownBy(() -> reads.discover(FOREIGN, new TopicDiscoveryQueryDto("[", null, 1, page.nextCursor())))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.resume(OWNER, new TopicPageQueryDto(1, page.nextCursor())))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto("Travel", null, 1, page.nextCursor())))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 6, null)))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.resume(OWNER, new TopicPageQueryDto(1, "x".repeat(513))))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThat(reads.discover(OWNER, new TopicDiscoveryQueryDto(null, CefrLevel.C1, 3, null)).items()).isEmpty();
        assertThat(reads.discover(FOREIGN, new TopicDiscoveryQueryDto("[", null, 5, null)).items())
                .extracting(TopicDiscoveryPageDto.RowDto::topicId).containsExactly(second.topic());
        assertThat(mapper.writeValueAsString(page)).doesNotContain("private-cover", "hidden-item", "foreign", "quizzes");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void invalidQueriesAndStaleAnchorHavePublicTypedFailure() {
        profiles();
        Fixture first = topic("First", CefrLevel.B1, 1, ContentStatus.PUBLISHED);
        topic("Second", CefrLevel.B1, 2, ContentStatus.PUBLISHED);
        var page = reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 1, null));
        assertThat(page.nextCursor()).isNotNull();
        assertThatThrownBy(() -> reads.discover(OWNER, null))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.resume(OWNER, null))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(null, new TopicDiscoveryQueryDto(null, null, 1, null)))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.resume(" ", new TopicPageQueryDto(1, null)))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.counts(null))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(" ", null, 1, null)))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 1, "!invalid")))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.resume(OWNER, new TopicPageQueryDto(1, "!invalid")))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(null, CefrLevel.A1, 1, page.nextCursor())))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);

        var release = mongoTemplate.findById(first.release(), TopicVersion.class);
        release.setStatus(ContentStatus.HIDDEN);
        mongoTemplate.save(release);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 1, page.nextCursor())))
                .isExactlyInstanceOf(TopicCompanionInvalidQueryException.class);
    }

    @Test void providerStateFailuresAreNotInvalidQueries() {
        profiles();
        Fixture f = topic("Broken manifest", CefrLevel.B1, 1, ContentStatus.PUBLISHED);
        var release = mongoTemplate.findById(f.release(), TopicVersion.class);
        release.setItemVersionIds(List.of(id()));
        mongoTemplate.save(release);
        assertThatThrownBy(() -> reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 1, null)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reads.resume(OWNER, new TopicPageQueryDto(1, null)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reads.counts(OWNER))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reads.discover("missing-profile", new TopicDiscoveryQueryDto(null, null, 1, null)))
                .isExactlyInstanceOf(TopicRequestException.class);
    }

    @Test void resumeRequiresCurrentOwnedLearningParentAndPinnedVersionAndResetSuppressesIt() {
        profiles();
        Fixture f = topic("Current", CefrLevel.B1, 1, ContentStatus.PUBLISHED);
        String parent = id(), active = id();
        mongoTemplate.insert(UserTopicAttempt.builder().id(parent).userId(OWNER).topicId(f.topic())
                .attemptType(AttemptType.LEARNING).scope(AttemptScope.ITEM).status(AttemptStatus.IN_PROGRESS)
                .targetTopicItemId(f.item()).currentTopicItemId(f.item()).build());
        mongoTemplate.insert(UserTopicItemAttempt.builder().id(active).userId(OWNER).topicId(f.topic()).topicItemId(f.item())
                .topicItemVersionId(f.version()).userTopicAttemptId(parent).status(AttemptStatus.IN_PROGRESS)
                .lastAccessedAt(AT).build());
        var before = snapshot();
        var page = reads.resume(OWNER, new TopicPageQueryDto(3, null));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().activeLearningItem().attemptId()).isEqualTo(active);
        assertThat(page.items().getFirst().nextEligibleItem()).isNull();
        assertThat(page.items().getFirst().lastAccessedAt()).isEqualTo(AT);
        assertThat(reads.resume(FOREIGN, new TopicPageQueryDto(3, null)).items()).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
        mongoTemplate.insert(TopicLearnerState.builder().id(generations.id(OWNER, f.topic()))
                .userId(OWNER).topicId(f.topic()).generation(1).build());
        assertThat(reads.resume(OWNER, new TopicPageQueryDto(3, null)).items()).isEmpty();
        assertThat(reads.discover(OWNER, new TopicDiscoveryQueryDto(null, null, 3, null)).items()).hasSize(1);
    }

    @Test void completionCountsResolveOfficialRetryAndExcludeOtherOwnerHiddenAndReset() {
        profiles();
        Fixture completed = topic("Completed", CefrLevel.B1, 1, ContentStatus.PUBLISHED);
        Fixture incomplete = topic("Incomplete", CefrLevel.B1, 2, ContentStatus.PUBLISHED);
        Fixture hidden = topic("Hidden", CefrLevel.B1, 3, ContentStatus.HIDDEN);
        Fixture reset = topic("Reset", CefrLevel.B1, 4, ContentStatus.PUBLISHED);
        Fixture foreign = topic("Foreign", CefrLevel.B1, 5, ContentStatus.PUBLISHED);
        String extra = id(), extraVersion = id();
        mongoTemplate.insert(TopicItemVersion.builder().id(extraVersion).topicId(incomplete.topic())
                .topicItemId(extra).status(ContentStatus.PUBLISHED).title("Next").order(2).build());
        var release = mongoTemplate.findById(incomplete.release(), TopicVersion.class);
        var manifest = new ArrayList<>(release.getItemVersionIds());
        manifest.add(extraVersion);
        release.setItemVersionIds(manifest);
        mongoTemplate.save(release);
        mongoTemplate.insert(progress(OWNER, completed, 0));
        mongoTemplate.insert(progress(OWNER, hidden, 0));
        mongoTemplate.insert(progress(OWNER, reset, 0));
        mongoTemplate.insert(progress(FOREIGN, foreign, 0));
        mongoTemplate.insert(TopicLearnerState.builder().id(generations.id(OWNER, reset.topic()))
                .userId(OWNER).topicId(reset.topic()).generation(1).build());
        var official = UserTopicItemAttempt.builder().id(id()).userId(OWNER).topicId(incomplete.topic())
                .topicItemId(incomplete.item()).topicItemVersionId(incomplete.version()).attemptType(AttemptType.RETRY)
                .status(AttemptStatus.COMPLETED).completedAt(AT).lastAccessedAt(AT)
                .completionResult(CompletionResultVo.builder().build()).build();
        var retry = UserTopicItemAttempt.builder().id(id()).userId(OWNER).topicId(incomplete.topic())
                .topicItemId(incomplete.item()).topicItemVersionId(incomplete.version()).attemptType(AttemptType.RETRY)
                .status(AttemptStatus.IN_PROGRESS).lastAccessedAt(AT).build();
        mongoTemplate.insert(TopicRetryRuntime.builder().id(id()).userId(OWNER).topicId(incomplete.topic())
                .status(RetryStatus.IN_PROGRESS).officialResults(Map.of(incomplete.item(), official)).currentAttempt(retry).build());
        var before = snapshot();
        var counts = reads.counts(OWNER);
        assertThat(counts.completedTopics()).isEqualTo(1);
        assertThat(counts.completedTopicItems()).isEqualTo(2);
        assertThat(counts.coverage()).isEqualTo("COMPLETE");
        var resume = reads.resume(OWNER, new TopicPageQueryDto(5, null));
        assertThat(resume.items()).hasSize(1);
        assertThat(resume.items().getFirst().activeLearningItem()).isNull();
        assertThat(resume.items().getFirst().nextEligibleItem().itemId()).isEqualTo(extra);
        assertThat(snapshot()).isEqualTo(before);
    }

    private void profiles() {
        for (String owner : List.of(OWNER, FOREIGN))
            mongoTemplate.insert(UserProfile.builder().id(owner).cefrLevel(CefrLevel.B1).build());
    }
    private Fixture topic(String title, CefrLevel level, int order, ContentStatus status) {
        String topic = id(), release = id(), item = id(), version = id(), hidden = id();
        mongoTemplate.insert(Topic.builder().id(topic).latestPublishedVersion(release).titleEn(title)
                .cefrLevel(level).orderIndex(order).coverUrl("private-cover").build());
        mongoTemplate.insert(TopicVersion.builder().id(release).topicId(topic).status(status)
                .itemVersionIds(List.of(version, hidden)).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(version).topicId(topic).topicItemId(item)
                .title("Lesson").status(ContentStatus.PUBLISHED).order(0).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(hidden).topicId(topic).topicItemId(id())
                .title("hidden-item").status(ContentStatus.HIDDEN).order(1).build());
        return new Fixture(topic, release, item, version);
    }
    private UserTopicItemProgress progress(String owner, Fixture f, long generation) {
        return UserTopicItemProgress.builder().id(id()).userId(owner).topicId(f.topic()).topicItemId(f.item())
                .generation(generation).status(ItemProgressStatus.COMPLETED).completedAt(AT)
                .completedTopicItemVersionId(f.version()).build();
    }
    private Map<String, List<Document>> snapshot() {
        Map<String, List<Document>> result = new TreeMap<>();
        for (String collection : mongoTemplate.getCollectionNames()) {
            if (collection.startsWith("topic") || collection.startsWith("user_topic"))
                result.put(collection, mongoTemplate.getCollection(collection).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return result;
    }
    private static String id() { return new ObjectId().toHexString(); }
    private record Fixture(String topic, String release, String item, String version) {}
}
