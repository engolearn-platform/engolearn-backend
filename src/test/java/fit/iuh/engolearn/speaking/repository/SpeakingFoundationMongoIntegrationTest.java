package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.SpeakingMongoIndexes;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.service.impl.QuotaServiceImpl;
import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.Create;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.Replace;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.Resume;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingTurnStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingOpeningVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import fit.iuh.engolearn.speaking.service.SpeakingSessions;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.topic.contract.TopicSpeakingAccess;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.topic.mapper.TopicSpeakingProfileMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicSpeakingProfile;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.UserTopicProgress;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.service.TopicSpeakingProfileService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import jakarta.servlet.http.Cookie;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"engolearn.speaking.recovery.enabled=false",
        "engolearn.security.jwt.secret=speaking-foundation-db-only-integration-secret"})
class SpeakingFoundationMongoIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String TOPIC_ID = "66fa4639f05f7400cafe0000";
    private static final SpeakingScopeDto TOPIC = new SpeakingScopeDto(SpeakingScopeType.TOPIC, TOPIC_ID, null);
    private static final String ITEM_ID = "66fa4639f05f7400cafe0001";
    private static final SpeakingScopeDto ITEM = new SpeakingScopeDto(SpeakingScopeType.TOPIC_ITEM, TOPIC_ID, ITEM_ID);
    private static final String LEARNER = "learner";
    private static final String AUTHOR = "author";
    private static final String ADMIN = "admin";
    private static final String OTHER = "other";
    private static final String PROFILE_URL = "/api/management/topics/" + TOPIC_ID + "/speaking-profile";
    private static final String ITEM_PROFILE_URL = "/api/management/topics/" + TOPIC_ID + "/items/" + ITEM_ID + "/speaking-profile";

    @Autowired private SpeakingSessions sessions;
    @Autowired private SpeakingRuntime runtime;
    @Autowired private SpeakingSessionRepository repository;
    @Autowired private SpeakingOwnerSlotRepository ownerSlots;
    @Autowired private SpeakingTurnRepository turns;
    @Autowired private SpeakingTurnArchive archive;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private fit.iuh.engolearn.media.contract.MediaManagement media;
    @Autowired private fit.iuh.engolearn.shared.security.CurrentUserProvider users;
    @Autowired private fit.iuh.engolearn.speaking.utils.SpeakingSessionSettings settings;
    @Autowired private fit.iuh.engolearn.speaking.utils.SpeakingProviderProperties providerProperties;
    @Autowired private fit.iuh.engolearn.speaking.utils.SpeakingConversation conversation;
    @Autowired private java.time.Clock clock;
    @Autowired private TopicSpeakingProfileService profiles;
    @Autowired private TopicSpeakingAccess access;
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockitoSpyBean private QuotaServiceImpl quota;

    @BeforeEach
    void fixture() {
        SpeakingMongoIndexes.initialize(mongoTemplate);
        account(LEARNER, UserRole.USER);
        account(OTHER, UserRole.USER);
        account(AUTHOR, UserRole.CONTENT_MANAGER);
        account(ADMIN, UserRole.ADMIN);
        mongoTemplate.insert(Topic.builder().id(TOPIC_ID).latestPublishedVersion("cafe-v1")
                .titleEn("Cafe").cefrLevel(CefrLevel.A1).revision(4L).build());
        mongoTemplate.insert(TopicVersion.builder().id("cafe-v1").topicId(TOPIC_ID).status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of("order-v1")).build());
        mongoTemplate.insert(TopicItem.builder().id(ITEM_ID).topicId(TOPIC_ID).build());
        mongoTemplate.insert(TopicItemVersion.builder().id("order-v1").topicId(TOPIC_ID).topicItemId(ITEM_ID)
                .status(ContentStatus.PUBLISHED).title("Order a drink").order(1)
                .vocabularies(List.of(ItemWordVo.builder().vocabId("juice")
                        .snapshot(WordSnapshotVo.builder().word("juice").build()).build())).build());

        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 0, 3600, 3600, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileRoutesRequireBothRolesAndPublishOnlyTheSelectedRevision() throws Exception {
        for (String path : List.of(PROFILE_URL, ITEM_PROFILE_URL)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(put(path).cookie(cookie(LEARNER)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":0,\"profile\":{\"enabled\":false}}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(post(path + "/publish").cookie(cookie(LEARNER)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":0}")).andExpect(status().isForbidden());
            mvc.perform(get(path).cookie(cookie(AUTHOR))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.revision").value(0))
                    .andExpect(jsonPath("$.data.resources[0].textEn").value("juice"))
                    .andExpect(jsonPath("$.data.resources[0].itemId").value(ITEM_ID))
                    .andExpect(jsonPath("$.data.initialProfile.enabled").value(false));
            mvc.perform(put(path).cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":0,\"profile\":{\"enabled\":false,\"selectedResourceIds\":[\"unreleased\"]}}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESOURCE"));
            mvc.perform(put(path).cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":0,\"profile\":{\"enabled\":false}}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
            mvc.perform(post(path + "/publish").cookie(cookie(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2))
                    .andExpect(jsonPath("$.data.published.enabled").value(false));
            mvc.perform(post(path + "/publish").cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":1}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_CONFLICT"));
        }
        authorize(LEARNER, UserRole.USER);
        var denied = new TopicSpeakingProfileDto(false, null, null, null, null, List.of(), List.of(), null, null);
        assertThatThrownBy(() -> profiles.save(TOPIC, 2, denied))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> profiles.publish(ITEM, 2)).isInstanceOf(AccessDeniedException.class);
        assertThat(mongoTemplate.findById(TOPIC_ID, Topic.class).getRevision()).isEqualTo(4L);
    }

    @Test
    void higherTopicBlocksNewItemEvenWithProgressAndExplicitResumeRemainsPinned() throws Exception {
        publish(ITEM, "Pinned speaking practice");
        authorize(LEARNER, UserRole.USER);
        var request = new Create(ITEM, UUID.randomUUID());
        String sessionId = sessions.create(request).session().sessionId();
        var inserted = mongoTemplate.insert(UserTopicItemProgress.builder()
                .userId(LEARNER).topicId(TOPIC_ID).topicItemId(ITEM_ID)
                .status(ItemProgressStatus.IN_PROGRESS).build());
        var progress = mongoTemplate.findById(inserted.getId(), UserTopicItemProgress.class);
        Topic topic = mongoTemplate.findById(TOPIC_ID, Topic.class);
        topic.setCefrLevel(CefrLevel.B2);
        mongoTemplate.save(topic);

        mvc.perform(get("/api/v1/speaking/availability").cookie(cookie(LEARNER))
                        .param("scopeType", "TOPIC_ITEM").param("topicId", TOPIC_ID).param("topicItemId", ITEM_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("LEVEL_TOO_HIGH"))
                .andExpect(jsonPath("$.data.activeSession.sessionId").value(sessionId));
        authorize(LEARNER, UserRole.USER);
        assertCode(() -> sessions.create(new Create(TOPIC, UUID.randomUUID())), "LEVEL_TOO_HIGH");
        assertCode(() -> sessions.create(new Create(ITEM, UUID.randomUUID())), "LEVEL_TOO_HIGH");
        assertCode(() -> sessions.replace(new Replace(ITEM, UUID.randomUUID(), sessionId, 1L)), "LEVEL_TOO_HIGH");
        assertThat(sessions.create(request).created()).isFalse();
        assertThat(sessions.history(ITEM, 0, 20).items()).singleElement().satisfies(row -> {
            assertThat(row.sessionId()).isEqualTo(sessionId);
            assertThat(row.controllerEpoch()).isEqualTo(1);
            assertThat(row.status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);
        });
        authorize(OTHER, UserRole.USER);
        assertCode(() -> sessions.resume(sessionId, new Resume(UUID.randomUUID(), 1L)), "ACCESS_DENIED");
        authorize(LEARNER, UserRole.USER);
        assertCode(() -> sessions.resume(sessionId, new Resume(UUID.randomUUID(), 2L)), "CONTROLLER_CHANGED");
        assertThat(sessions.resume(sessionId, new Resume(UUID.randomUUID(), 1L))).satisfies(resumed -> {
            assertThat(resumed.controllerEpoch()).isEqualTo(2);
            assertThat(resumed.context().description()).isEqualTo("Pinned speaking practice");
        });
        assertThat(mongoTemplate.findAll(UserTopicItemProgress.class))
                .singleElement().usingRecursiveComparison().isEqualTo(progress);
        assertThat(mongoTemplate.findAll(UserTopicAttempt.class)).isEmpty();
        assertThat(mongoTemplate.findAll(UserTopicProgress.class)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = CefrLevel.class, names = {"A1", "A2", "B1"})
    void b1LearnerCanPracticeReleasedTopicWithoutEnrolling(CefrLevel topicLevel) {
        Topic topic = mongoTemplate.findById(TOPIC_ID, Topic.class);
        topic.setCefrLevel(topicLevel);
        mongoTemplate.save(topic);
        UserProfile learner = mongoTemplate.findById(LEARNER, UserProfile.class);
        learner.setCefrLevel(CefrLevel.B1);
        mongoTemplate.save(learner);
        publish(TOPIC, "Published for practice");
        var topicBefore = mongoTemplate.findById(TOPIC_ID, Topic.class);
        var releaseBefore = mongoTemplate.findById("cafe-v1", TopicVersion.class);
        var itemBefore = mongoTemplate.findById("order-v1", TopicItemVersion.class);

        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(TOPIC).available()).isTrue();
        var session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session();
        assertThat(sessions.history(TOPIC, 0, 20).items()).hasSize(1);
        sessions.end(session.sessionId(), session.controllerEpoch());
        sessions.delete(session.sessionId());
        assertThat(mongoTemplate.findAll(UserTopicAttempt.class)).isEmpty();
        assertThat(mongoTemplate.findAll(UserTopicProgress.class)).isEmpty();
        assertThat(mongoTemplate.findAll(UserTopicItemProgress.class)).isEmpty();
        assertThat(mongoTemplate.findById(TOPIC_ID, Topic.class)).usingRecursiveComparison().isEqualTo(topicBefore);
        assertThat(mongoTemplate.findById("cafe-v1", TopicVersion.class)).usingRecursiveComparison().isEqualTo(releaseBefore);
        assertThat(mongoTemplate.findById("order-v1", TopicItemVersion.class)).usingRecursiveComparison().isEqualTo(itemBefore);
    }

    @Test
    void topicPracticeRemainsAvailableAfterLearningProgressAndCompletion() {
        publish(TOPIC, "Published for practice");
        mongoTemplate.insert(UserTopicProgress.builder().userId(LEARNER).topicId(TOPIC_ID).build());
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(TOPIC).available()).isTrue();
        var first = sessions.create(new Create(TOPIC, UUID.randomUUID())).session();
        mongoTemplate.insert(UserTopicItemProgress.builder().userId(LEARNER).topicId(TOPIC_ID).topicItemId(ITEM_ID)
                .status(ItemProgressStatus.COMPLETED).build());
        assertThat(sessions.availability(TOPIC).available()).isTrue();
        var replacement = sessions.replace(new Replace(TOPIC, UUID.randomUUID(), first.sessionId(), 1L));
        assertThat(replacement.session().sessionId()).isNotEqualTo(first.sessionId());
    }

    @Test
    void noPublishedProfileAndNoAuthoritativeLevelDenyNewPractice() {
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        profiles.save(TOPIC, 0, profile(TOPIC, "Draft only"));
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(TOPIC).reason()).isEqualTo("PROFILE_DISABLED");
        assertCode(() -> sessions.create(new Create(TOPIC, UUID.randomUUID())), "PROFILE_DISABLED");
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        profiles.publish(TOPIC, 1);
        mongoTemplate.remove(mongoTemplate.findById(LEARNER, UserProfile.class));
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(TOPIC).reason()).isEqualTo("SCOPE_FORBIDDEN");
        assertCode(() -> sessions.create(new Create(TOPIC, UUID.randomUUID())), "SCOPE_FORBIDDEN");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void publishedSameParentItemReportsLockedButHiddenItemReportsOnlyForbidden() {
        String secondId = "66fa4639f05f7400cafe0002";
        var scope = new SpeakingScopeDto(SpeakingScopeType.TOPIC_ITEM, TOPIC_ID, secondId);
        mongoTemplate.insert(TopicItem.builder().id(secondId).topicId(TOPIC_ID).build());
        TopicItemVersion second = mongoTemplate.insert(TopicItemVersion.builder().id("second-v1")
                .topicId(TOPIC_ID).topicItemId(secondId).status(ContentStatus.PUBLISHED).title("Second").order(2)
                .vocabularies(List.of(ItemWordVo.builder().vocabId("juice")
                        .snapshot(WordSnapshotVo.builder().word("juice").build()).build())).build());
        TopicVersion release = mongoTemplate.findById("cafe-v1", TopicVersion.class);
        release.setItemVersionIds(List.of("order-v1", "second-v1"));
        mongoTemplate.save(release);
        publish(scope, "Second item");
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(scope).reason()).isEqualTo("ITEM_LOCKED");
        assertCode(() -> sessions.create(new Create(scope, UUID.randomUUID())), "ITEM_LOCKED");
        second.setStatus(ContentStatus.HIDDEN);
        mongoTemplate.save(second);
        assertThat(sessions.availability(scope).reason()).isEqualTo("SCOPE_FORBIDDEN");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void failedReplacementKeepsCurrentSessionAndPublishedContextStaysPinned() {
        publish(TOPIC, "Original");
        authorize(LEARNER, UserRole.USER);
        var request = new Create(TOPIC, UUID.randomUUID());
        var first = sessions.create(request);
        assertThat(first.created()).isTrue();
        assertThat(first.session().status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);
        String original = first.session().sessionId();
        assertThat(sessions.create(request).created()).isFalse();
        assertCode(() -> sessions.create(new Create(ITEM, request.clientRequestId())), "REQUEST_CONFLICT");
        assertCode(() -> sessions.replace(new Replace(ITEM, UUID.randomUUID(), original, 1L)), "PROFILE_DISABLED");
        assertThat(sessions.read(original).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);

        publish(ITEM, "Published item");
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        profiles.save(TOPIC, 2, profile(TOPIC, "Updated"));
        profiles.publish(TOPIC, 3);
        authorize(LEARNER, UserRole.USER);
        var request2 = new Replace(ITEM, UUID.randomUUID(), original, 1L);
        var replacement = sessions.replace(request2);
        assertThat(replacement.session().sessionId()).isNotEqualTo(original);
        assertThat(replacement.session().context().description()).isEqualTo("Published item");
        assertThat(sessions.replace(request2).session().sessionId()).isEqualTo(replacement.session().sessionId());
        assertThat(sessions.create(request).session().context().description()).isEqualTo("Original");
        assertThat(sessions.history(TOPIC, 0, 20).items()).extracting(row -> row.sessionId()).containsExactly(original);

        TopicVersion release = mongoTemplate.findById("cafe-v1", TopicVersion.class);
        release.setStatus(ContentStatus.HIDDEN);
        mongoTemplate.save(release);
        assertThat(access.resolve(LEARNER, TOPIC).reason().name()).isEqualTo("SCOPE_FORBIDDEN");
        assertThat(sessions.read(original).context().description()).isEqualTo("Original");
        sessions.delete(original);
        assertCode(() -> sessions.create(request), "SESSION_NOT_FOUND");
        assertThat(sessions.history(TOPIC, 0, 20).items()).isEmpty();
    }

    @Test
    void claimsOneOwnerSlotAndFencesTheLosingController() throws Exception {
        publish(TOPIC, "Cafe");
        var request = new Create(TOPIC, UUID.randomUUID());
        Callable<SpeakingSessions.StartResult> start = () -> asLearner(() -> sessions.create(request));
        var created = simultaneous(start, start);
        assertThat(created).extracting(SpeakingSessions.StartResult::created).containsExactlyInAnyOrder(true, false);
        String session = created.getFirst().session().sessionId();
        assertThat(created.getLast().session().sessionId()).isEqualTo(session);
        assertThat(repository.findAll()).hasSize(1);
        assertThatThrownBy(() -> repository.insert(SpeakingSession.builder().id("another")
                .ownerId(LEARNER).unfinishedOwner(LEARNER).clientRequestId(UUID.randomUUID().toString())
                .status(SpeakingSessionStatus.INITIALIZING).build())).isInstanceOf(DuplicateKeyException.class);

        authorize(LEARNER, UserRole.USER);
        var ticket = sessions.ticket(session, 1);
        var claims = simultaneous(() -> consume(session, ticket.ticket()), () -> consume(session, ticket.ticket()));
        assertThat(claims.stream().filter(SpeakingRuntime.Lease.class::isInstance)).hasSize(1);
        assertThat(claims).contains("INVALID_TICKET");
        var lease = claims.stream().filter(SpeakingRuntime.Lease.class::isInstance)
                .map(SpeakingRuntime.Lease.class::cast).findFirst().orElseThrow();

        var resume = new Resume(UUID.randomUUID(), 1L);
        var otherResume = new Resume(UUID.randomUUID(), 1L);
        var resumed = simultaneous(() -> tryResume(session, resume), () -> tryResume(session, otherResume));
        assertThat(resumed).contains("CONTROLLER_CHANGED");
        assertCode(() -> runtime.state(lease), "CONTROLLER_CHANGED");
        assertCode(() -> sessions.ticket(session, 1), "CONTROLLER_CHANGED");
        assertThat(sessions.read(session).controllerEpoch()).isEqualTo(2);
    }

    @Test
    void exhaustedQuotaClosesSessionAndBlocksResumeAndReconnect() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        runtime.completeOpening(session, new SpeakingOpeningVo("Hello", null, SpeakingAudioStatus.TEXT_READY, null));
        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 3600, 3600, 0, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertCode(() -> sessions.resume(session, new Resume(UUID.randomUUID(), 1L)), "DAILY_QUOTA_REACHED");
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.ENDED);
        assertCode(() -> sessions.ticket(session, 1), "SESSION_ENDED");
        assertThat(sessions.history(TOPIC, 0, 20).items()).hasSize(1);
    }

    @Test
    void quotaOutageBlocksResumeWithoutChangingSessionAndKeepsReadAvailable() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();

        org.mockito.Mockito.doThrow(new QuotaUnavailableException()).when(quota)
                .checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertCode(() -> sessions.resume(session, new Resume(UUID.randomUUID(), 1L)), "QUOTA_UNAVAILABLE");
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);
        assertThat(sessions.read(session).controllerEpoch()).isEqualTo(1);
        assertCode(() -> sessions.ticket(session, 1), "QUOTA_UNAVAILABLE");
        assertThat(sessions.read(session).controllerEpoch()).isEqualTo(1);
    }

    @Test
    void exhaustedQuotaBlocksTicketIssuanceAndEndsSession() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 3600, 3600, 0, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertCode(() -> sessions.ticket(session, 1), "DAILY_QUOTA_REACHED");
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.ENDED);
    }

    @Test
    void resumeReplayDoesNotRecheckQuotaOrCloseAnAlreadyResumedSession() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        var request = new Resume(UUID.randomUUID(), 1L);
        assertThat(sessions.resume(session, request).controllerEpoch()).isEqualTo(2);
        org.mockito.Mockito.clearInvocations(quota);
        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 3600, 3600, 0, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertThat(sessions.resume(session, request).controllerEpoch()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(quota);
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);
    }

    @Test
    void quotaDenialCannotCloseSessionAfterControllerEpochChanges() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        org.mockito.Mockito.doAnswer(invocation -> {
            SpeakingSession current = repository.findById(session).orElseThrow();
            current.setControllerEpoch(2L);
            repository.save(current);
            return new AvailabilityResultDto("SPEAKING_DURATION", 3600, 3600, 0, "SECOND", "DAILY",
                    Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE");
        }).when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertCode(() -> sessions.ticket(session, 1), "CONTROLLER_CHANGED");
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);
        assertThat(sessions.read(session).controllerEpoch()).isEqualTo(2);
    }

    @Test
    void exhaustedQuotaBetweenTicketAndSocketConnectionBlocksReconnect() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        var ticket = sessions.ticket(session, 1);
        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 3600, 3600, 0, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(LEARNER, "SPEAKING_DURATION");

        assertCode(() -> runtime.consumeTicket(session, ticket.ticket()), "DAILY_QUOTA_REACHED");
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.ENDED);
        assertThat(sessions.history(TOPIC, 0, 20).items()).hasSize(1);
    }

    @Test
    void closingSessionCanReconnectToObserveAcceptedTurn() {
        publish(TOPIC, "Cafe");
        publish(ITEM, "Order");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        runtime.completeOpening(session, new SpeakingOpeningVo("Hello", null, SpeakingAudioStatus.TEXT_READY, null));
        var lease = runtime.consumeTicket(session, sessions.ticket(session, 1).ticket());
        var turn = runtime.admit(lease, UUID.randomUUID());
        runtime.recordingStarted(lease, turn.turnId());
        runtime.seal(lease, turn.turnId(), null, 3500);
        assertThat(sessions.replace(new Replace(ITEM, UUID.randomUUID(), session, 1L)).replacingSessionId())
                .isEqualTo(session);
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.CLOSING);

        runtime.releaseLease(lease);
        var reconnect = runtime.consumeTicket(session, sessions.ticket(session, 1).ticket());
        assertThat(runtime.state(reconnect).status()).isEqualTo(SpeakingSessionStatus.CLOSING);
    }

    @Test
    void replacementWaitsForFinalizedTurnCutoffAndRejectsLateWork() {
        publish(TOPIC, "Cafe");
        publish(ITEM, "Order");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        assertThat(runtime.completeOpening(session, new SpeakingOpeningVo("Hello", null, SpeakingAudioStatus.TEXT_READY, null))).isTrue();
        assertThat(runtime.completeOpening(session, new SpeakingOpeningVo("Duplicate", null, SpeakingAudioStatus.TEXT_READY, null))).isFalse();
        var lease = runtime.consumeTicket(session, sessions.ticket(session, 1).ticket());
        var first = runtime.admit(lease, UUID.randomUUID());
        assertCode(() -> runtime.admit(lease, UUID.randomUUID()), "TURN_IN_PROGRESS");
        runtime.recordingStarted(lease, first.turnId());
        var work = runtime.seal(lease, first.turnId(), null, 3500);
        runtime.transcript(work, "One juice, please.");

        var replacement = new Replace(ITEM, UUID.randomUUID(), session, 1L);
        assertThat(sessions.replace(replacement).replacingSessionId()).isEqualTo(session);
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.CLOSING);
        assertCode(() -> sessions.create(new Create(TOPIC, UUID.randomUUID())), "ACTIVE_SESSION_EXISTS");

        SpeakingSession before = repository.findById(session).orElseThrow();
        SpeakingSession stale = repository.findById(session).orElseThrow();
        before.setClosingCutoff(Instant.now().minusSeconds(1));
        repository.save(before);
        stale.setControllerEpoch(99);
        assertThatThrownBy(() -> repository.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertCode(() -> runtime.settle(work, SpeakingTurnStatus.COMMITTED, null, "Late", null, null), "STALE_TURN");
        runtime.recover();
        assertThat(sessions.read(session).status()).isEqualTo(SpeakingSessionStatus.ENDED);
        assertThat(sessions.read(session).finalizedTurnCount()).isEqualTo(1);
        assertThat(sessions.turns(session, 0, 20).items()).singleElement().satisfies(turn -> {
            assertThat(turn.transcript()).isEqualTo("One juice, please.");
            assertThat(turn.status()).isEqualTo(SpeakingTurnStatus.FAILED);
        });
        assertThat(sessions.replace(replacement).session().scope()).isEqualTo(ITEM);
    }

    @Test
    void incompleteSpeakingDraftsBlockOnlySpeakingPublicationAtBothScopes() throws Exception {
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var invalid = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Description", "AI", "Learner",
                List.of(), List.of(), "", null);
        for (var scope : List.of(TOPIC, ITEM)) {
            assertThat(profiles.save(scope, 0, invalid).publishViolations())
                    .extracting("path").containsExactly("profile.fallbackReply");
            assertCodeTopic(() -> profiles.publish(scope, 1), "INVALID_PROFILE");
            assertThat(profiles.read(scope).published()).isNull();
        }
        mvc.perform(get("/api/management/topics/" + TOPIC_ID + "/readiness").cookie(cookie(AUTHOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations[?(@.path =~ /.*speakingProfile.*/)]").isEmpty());
        assertThat(access.resolve(LEARNER, new SpeakingScopeDto(SpeakingScopeType.TOPIC_ITEM, "another-topic", ITEM_ID))
                .reason().name()).isEqualTo("SCOPE_FORBIDDEN");
    }

    @Test
    void issuedProfileSurvivesCompatibleContentReleaseAndPinsEachSession() {
        String firstId = createInitialTopicSession();
        var original = repository.findById(firstId).orElseThrow().getSnapshot();
        assertThat(original.getResources()).extracting(resource -> resource.getTextEn()).containsExactly("juice");

        Topic topic = mongoTemplate.findById(TOPIC_ID, Topic.class);
        mongoTemplate.insert(TopicItemVersion.builder().id("order-v2").topicId(TOPIC_ID).topicItemId(ITEM_ID)
                .status(ContentStatus.PUBLISHED).title("Order a drink").order(1)
                .vocabularies(List.of(ItemWordVo.builder().vocabId("juice")
                        .snapshot(WordSnapshotVo.builder().word("juice").sense("A drink from fruit").build()).build())).build());
        mongoTemplate.insert(TopicVersion.builder().id("cafe-v2").topicId(TOPIC_ID).status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of("order-v2")).build());
        topic.setLatestPublishedVersion("cafe-v2");
        mongoTemplate.save(topic);

        var decision = access.resolve(LEARNER, TOPIC);
        assertThat(decision.reason()).isNull();
        assertThat(decision.snapshot().profileVersion()).isEqualTo(original.getProfileVersion());
        var replaced = sessions.replace(new Replace(TOPIC, UUID.randomUUID(), firstId, 1L)).session();
        var updated = repository.findById(replaced.sessionId()).orElseThrow().getSnapshot();
        assertThat(updated.getContentVersion()).isEqualTo("cafe-v2");
        assertThat(updated.getResources()).extracting(resource -> resource.getContext()).containsExactly("A drink from fruit");
        assertThat(repository.findById(firstId).orElseThrow().getSnapshot()).usingRecursiveComparison().isEqualTo(original);

        TopicItemVersion second = mongoTemplate.findById("order-v2", TopicItemVersion.class);
        second.getVocabularies().getFirst().getSnapshot().setSense("x".repeat(530_000));
        mongoTemplate.save(second);
        assertCode(() -> sessions.replace(new Replace(TOPIC, UUID.randomUUID(), replaced.sessionId(), 1L)),
                "CONTEXT_BUDGET_EXCEEDED");
        assertThat(sessions.read(replaced.sessionId()).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);

        second.setVocabularies(List.of());
        mongoTemplate.save(second);
        assertCode(() -> sessions.replace(new Replace(TOPIC, UUID.randomUUID(), replaced.sessionId(), 1L)),
                "PROFILE_RESOURCES_STALE");
        assertThat(sessions.read(replaced.sessionId()).status()).isEqualTo(SpeakingSessionStatus.INITIALIZING);

        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var stale = profiles.read(TOPIC);
        assertThat(stale.publishedViolations()).extracting("code").containsExactly("INVALID_RESOURCE");
        profiles.save(TOPIC, stale.revision(), stale.draft());
        assertCodeTopic(() -> profiles.publish(TOPIC, stale.revision() + 1), "INVALID_PROFILE");
        assertThat(profiles.read(TOPIC).publishedProfileVersion()).isEqualTo(stale.publishedProfileVersion());

        second.setVocabularies(List.of(ItemWordVo.builder().vocabId("water").snapshot(
                WordSnapshotVo.builder().word("water").build()).build()));
        mongoTemplate.save(second);
        var current = profiles.read(TOPIC);
        assertThat(current.draft().selectedResourceIds()).containsExactlyElementsOf(stale.draft().selectedResourceIds());
        assertThat(current.resources()).extracting(resource -> resource.textEn()).containsExactly("water");
        var repaired = profiles.save(TOPIC, current.revision(), profile(TOPIC, "Repaired"));
        var published = profiles.publish(TOPIC, repaired.revision());
        assertThat(access.resolve(LEARNER, TOPIC).snapshot().resources()).singleElement()
                .satisfies(resource -> assertThat(resource.textEn()).isEqualTo("water"));

        var disabled = new TopicSpeakingProfileDto(false, null, null, null, null, List.of(), List.of(), null, null);
        var disabledDraft = profiles.save(TOPIC, published.revision(), disabled);
        profiles.publish(TOPIC, disabledDraft.revision());
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.availability(TOPIC).reason()).isEqualTo("PROFILE_DISABLED");
        assertThat(repository.findById(firstId).orElseThrow().getSnapshot()).usingRecursiveComparison().isEqualTo(original);
        assertThat(repository.findById(replaced.sessionId()).orElseThrow().getSnapshot())
                .usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void readsAndPublishesExactScopeWithoutWritingToLearningCollections() {
        authorize(ADMIN, UserRole.ADMIN);
        var snapshot = learningState();
        var initial = profiles.read(ITEM);
        assertThat(initial.initialProfile().suggestedTopics()).containsExactly("Order a drink");
        assertThat(initial.initialProfile().level()).isEqualTo(CefrLevel.A1);
        assertThat(initial.initialProfile().selectedResourceIds()).isEmpty();
        assertThat(profiles.read(TOPIC).initialProfile().suggestedTopics()).containsExactly("Cafe");
        assertThat(mongoTemplate.findAll(Document.class, "topic_speaking_profiles")).isEmpty();

        profiles.save(ITEM, 0, profile(ITEM, "Item only"));
        profiles.publish(ITEM, 1);
        assertThat(profiles.read(TOPIC).published()).isNull();
        assertThat(learningState()).isEqualTo(snapshot);
        assertThat(mongoTemplate.findAll(Document.class, "topic_speaking_profiles")).singleElement()
                .satisfies(document -> {
                    assertThat(document.getList("publication_audit", Document.class)).singleElement().satisfies(audit -> {
                        assertThat(audit.getString("actor_id")).isEqualTo(ADMIN);
                        assertThat(audit.getLong("profile_version")).isEqualTo(1L);
                        assertThat(audit.getDate("published_at")).isNotNull();
                    });
                });
    }

    @Test
    void selectedOccurrencesStayIndependentAndOnlyEquivalentContextIsDeduplicated() {
        String[] items = {"66fa4639f05f7400cafe0002", "66fa4639f05f7400cafe0003"};
        for (int index = 0; index < items.length; index++) {
            String item = items[index];
            mongoTemplate.insert(TopicItem.builder().id(item).topicId(TOPIC_ID).build());
            mongoTemplate.insert(TopicItemVersion.builder().id("context-" + index)
                    .topicId(TOPIC_ID).topicItemId(item).status(ContentStatus.PUBLISHED).title("Context " + index)
                    .order(index + 2).vocabularies(List.of(
                            ItemWordVo.builder().vocabId("juice").senseId(index == 0 ? null : "electricity")
                                    .snapshot(WordSnapshotVo.builder().word("juice")
                                            .sense(index == 0 ? null : "Electrical power").build()).build(),
                            ItemWordVo.builder().vocabId("unselected").snapshot(
                                    WordSnapshotVo.builder().word("Unselected vocabulary").build()).build())).build());
        }
        TopicVersion manifest = mongoTemplate.findById("cafe-v1", TopicVersion.class);
        manifest.setItemVersionIds(List.of("order-v1", "context-0", "context-1"));
        mongoTemplate.save(manifest);

        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var selected = profiles.read(TOPIC).resources().stream().filter(resource -> resource.textEn().equals("juice"))
                .map(resource -> resource.id()).toList();
        assertThat(selected).hasSize(3).doesNotHaveDuplicates();
        var profile = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Order a drink", "Barista", "Customer",
                List.of(), selected, "Good morning.", null);
        profiles.save(TOPIC, 0, profile);
        profiles.publish(TOPIC, 1);
        assertThat(profiles.read(TOPIC).published().selectedResourceIds()).containsExactlyElementsOf(selected);
        assertThat(profiles.read(ITEM).published()).isNull();

        authorize(LEARNER, UserRole.USER);
        var created = sessions.create(new Create(TOPIC, UUID.randomUUID())).session();
        var snapshot = repository.findById(created.sessionId()).orElseThrow().getSnapshot();
        assertThat(snapshot.getResources()).hasSize(2);
        assertThat(snapshot.getResources()).extracting(resource -> resource.getContext())
                .containsExactly(null, "Electrical power");
        assertThat(conversation.opening(snapshot).data()).contains("juice", "Electrical power", "Barista")
                .doesNotContain("Unselected vocabulary");
    }

    @Test
    void unpublishedScopeUsesSavedSourceAndWrongParentIsRejectedForEveryOperation() throws Exception {
        String topicId = "66fa4639f05f7400cafe0010";
        String itemId = "66fa4639f05f7400cafe0011";
        mongoTemplate.insert(Topic.builder().id(topicId).titleVi("Chủ đề nháp").cefrLevel(CefrLevel.A2).build());
        mongoTemplate.insert(TopicItem.builder().id(itemId).topicId(topicId).build());
        var itemDraft = mongoTemplate.insert(TopicItemVersion.builder().id("not-released").topicId(topicId).topicItemId(itemId)
                .title("Draft lesson").status(ContentStatus.DRAFT).context(
                        fit.iuh.engolearn.topic.model.vo.TopicContextVo.builder()
                                .scenarioDescription("Practice a draft scenario.").build())
                .vocabularies(List.of(ItemWordVo.builder()
                        .snapshot(WordSnapshotVo.builder().word("ticket").build()).build()))
                .expressions(List.of(fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo.builder().purposes(List.of(
                        fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo.builder().examples(List.of(
                                fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo.builder()
                                        .textEn("May I buy a ticket?").build())).build())).build())).build());
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var scope = new SpeakingScopeDto(SpeakingScopeType.TOPIC_ITEM, topicId, itemId);
        var state = profiles.read(scope);
        assertThat(state.parentTitle()).isEqualTo("Chủ đề nháp");
        assertThat(state.resources()).extracting(resource -> resource.textEn())
                .containsExactly("ticket", "May I buy a ticket?");
        var topicScope = new SpeakingScopeDto(SpeakingScopeType.TOPIC, topicId, null);
        assertThat(profiles.read(topicScope).resources()).isEqualTo(state.resources());
        assertThat(state.initialProfile().description()).isEqualTo("Practice a draft scenario.");
        assertThat(state.initialProfile().level()).isEqualTo(CefrLevel.A2);
        assertThat(mongoTemplate.findAll(Document.class, "topic_speaking_profiles")).isEmpty();
        var selected = profile(scope, "Practice a draft scenario.");
        profiles.save(scope, 0, selected);
        profiles.save(topicScope, 0, selected);
        assertCodeTopic(() -> profiles.publish(scope, 1), "CONTENT_NOT_RELEASED");
        assertCodeTopic(() -> profiles.publish(topicScope, 1), "CONTENT_NOT_RELEASED");
        itemDraft.setExpressions(List.of());
        mongoTemplate.save(itemDraft);
        var updated = profiles.read(scope);
        assertThat(updated.resources()).extracting(resource -> resource.id())
                .containsExactly(selected.selectedResourceIds().getFirst());
        assertThat(updated.draft()).isEqualTo(selected);
        assertThat(updated.publishViolations()).extracting("code").contains("INVALID_RESOURCE");
        assertThat(profiles.save(scope, 1, selected).draft()).isEqualTo(selected);

        String wrongPath = "/api/management/topics/" + TOPIC_ID + "/items/" + itemId + "/speaking-profile";
        mvc.perform(get(wrongPath).cookie(cookie(AUTHOR))).andExpect(status().isNotFound());
        mvc.perform(put(wrongPath).cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"profile\":{\"enabled\":false}}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(wrongPath + "/publish").cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0}")).andExpect(status().isNotFound());
    }

    @Test
    void legacyVocabularyMatchesOnlyAnUnambiguousOccurrenceFromTheOriginalScope() {
        String sibling = "66fa4639f05f7400cafe0002";
        mongoTemplate.insert(TopicItem.builder().id(sibling).topicId(TOPIC_ID).build());
        mongoTemplate.insert(TopicItemVersion.builder().id("duplicate-vocab").topicId(TOPIC_ID).topicItemId(sibling)
                .status(ContentStatus.PUBLISHED).title("Sibling").order(2)
                .vocabularies(List.of(ItemWordVo.builder().vocabId("juice")
                        .snapshot(WordSnapshotVo.builder().word("juice").build()).build())).build());
        TopicVersion manifest = mongoTemplate.findById("cafe-v1", TopicVersion.class);
        manifest.setItemVersionIds(List.of("order-v1", "duplicate-vocab"));
        mongoTemplate.save(manifest);
        var legacy = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Cafe", "Barista", "Customer",
                List.of(), List.of("juice"), "Good morning.", null);
        String topicKey = scopeKey(TOPIC_ID);
        mongoTemplate.insert(TopicSpeakingProfile.builder().id(topicKey).published(TopicSpeakingProfileMapper.toValue(legacy))
                .publishedContentVersion("cafe-v1").publishedProfileVersion(1L).build());
        mongoTemplate.insert(TopicSpeakingProfile.builder().id(topicKey + "." + scopeKey(ITEM_ID))
                .published(TopicSpeakingProfileMapper.toValue(legacy)).publishedContentVersion("order-v1")
                .publishedProfileVersion(1L).build());

        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var topicProfile = profiles.read(TOPIC);
        assertThat(topicProfile.published().selectedResourceIds()).containsExactly("juice");
        assertThat(topicProfile.publishedViolations()).extracting("code").containsExactly("INVALID_RESOURCE");
        var itemProfile = profiles.read(ITEM);
        assertThat(itemProfile.publishedViolations()).isEmpty();
        assertThat(itemProfile.published().selectedResourceIds()).containsExactly(itemProfile.resources().getFirst().id());
        assertThat(mongoTemplate.findById(topicKey + "." + scopeKey(ITEM_ID), TopicSpeakingProfile.class)
                .getPublished().getSelectedResourceIds()).containsExactly("juice");
    }

    private static String scopeKey(String id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private String createInitialTopicSession() {
        publish(TOPIC, "Published context");
        authorize(LEARNER, UserRole.USER);
        return sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
    }

    private List<List<Document>> learningState() {
        return List.of(Topic.class, TopicVersion.class, TopicItem.class, TopicItemVersion.class,
                        UserTopicProgress.class, UserTopicItemProgress.class, UserTopicAttempt.class)
                .stream().map(type -> mongoTemplate.findAll(Document.class, mongoTemplate.getCollectionName(type)))
                .toList();
    }

    @Test
    void restCreateReplaysWithoutOpeningAndDeniesCrossOwnerAccess() throws Exception {
        publish(TOPIC, "Cafe");
        String body = """
                {"scope":{"type":"TOPIC","topicId":"%s","topicItemId":null},"clientRequestId":"%s"}
                """.formatted(TOPIC_ID, UUID.randomUUID());
        String sessionsPath = "/api/v1/speaking/sessions";
        mvc.perform(post(sessionsPath).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String json = mvc.perform(post(sessionsPath).cookie(cookie(LEARNER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.scope.topicId").value(TOPIC_ID))
                .andExpect(jsonPath("$.data.status").value("INITIALIZING")).andExpect(jsonPath("$.data.opening").isEmpty())
                .andExpect(jsonPath("$.data.startedAt").isEmpty()).andExpect(jsonPath("$.data.limits.maxTurns").value(30))
                .andReturn().getResponse().getContentAsString();
        String id = new tools.jackson.databind.ObjectMapper().readTree(json).path("data").path("sessionId").asString();
        mvc.perform(post(sessionsPath).cookie(cookie(LEARNER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value(id));
        mvc.perform(get(sessionsPath + "/" + id + "/opening/audio").cookie(cookie(OTHER)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(delete(sessionsPath + "/" + id).cookie(cookie(LEARNER)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(sessionsPath + "/" + id).cookie(cookie(LEARNER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void deletingSessionPersistsSealedTerminalMarkerForQuotaRecovery() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String sessionId = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        String turnId = UUID.randomUUID().toString();
        var marker = SpeakingTurnVo.builder().turnId(turnId).sequence(1).revision(1L)
                .status(SpeakingTurnStatus.FAILED).sealedAt(Instant.now()).speechDurationMs(1_500).build();
        var current = repository.findById(sessionId).orElseThrow();
        current.setActiveTurn(marker);
        repository.save(current);

        sessions.delete(sessionId);

        assertThat(turns.findById(turnId)).isPresent();
        assertThat(turns.findById(turnId).orElseThrow().getValue().getSealedAt()).isNotNull();
        assertThat(repository.findById(sessionId).orElseThrow().getActiveTurn()).isNull();
    }

    @Test
    void deletingSealedNonterminalTurnPreservesMeasuredSpeechAndWaitForRecovery() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String sessionId = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        String turnId = UUID.randomUUID().toString();
        var marker = SpeakingTurnVo.builder().turnId(turnId).sequence(1).revision(2L)
                .status(SpeakingTurnStatus.GENERATING).sealedAt(Instant.now()).speechDurationMs(1_500)
                .successfulWaitMs(2_500).build();
        var current = repository.findById(sessionId).orElseThrow();
        current.setActiveTurn(marker);
        repository.save(current);

        sessions.delete(sessionId);

        var obligation = turns.findById(turnId).orElseThrow();
        assertThat(obligation.getValue().getStatus()).isEqualTo(SpeakingTurnStatus.FAILED);
        assertThat(obligation.getValue().getSpeechDurationMs()).isEqualTo(1_500);
        assertThat(obligation.getValue().getSuccessfulWaitMs()).isEqualTo(2_500);
    }

    @Test
    void missingAuthoritativeSpeakingRuleHasASeparateAvailabilityCode() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        org.mockito.Mockito.doThrow(new QuotaUnavailableException(true)).when(quota)
                .checkAvailability(LEARNER, "SPEAKING_DURATION");
        assertCode(() -> sessions.create(new Create(TOPIC, UUID.randomUUID())), "QUOTA_NOT_CONFIGURED");
    }

    @Test
    void privatePlaybackRequiresLiveOwnedSessionAndRespectsThirtyDayRetention() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        String session = sessions.create(new Create(TOPIC, UUID.randomUUID())).session().sessionId();
        Instant now = clock.instant();
        var ref = new fit.iuh.engolearn.speaking.model.vo.SpeakingMediaVo("private-opening", now);
        runtime.completeOpening(session, new SpeakingOpeningVo("Hello", null, SpeakingAudioStatus.AUDIO_READY, ref));
        org.mockito.Mockito.when(media.resolve("private-opening")).thenReturn(new fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto(
                java.net.URI.create("https://media.example.test/signed"), now.plusSeconds(60), "audio/wav"));
        authorize(OTHER, UserRole.USER);
        assertCode(() -> sessions.openingAudio(session), "ACCESS_DENIED");
        org.mockito.Mockito.verifyNoInteractions(media);
        authorize(LEARNER, UserRole.USER);
        assertThat(sessions.openingAudio(session).expiresInSeconds()).isBetween(1L, 60L);

        var current = repository.findById(session).orElseThrow();
        current.getOpening().getAudio().setStoredAt(now.minus(java.time.Duration.ofDays(30)));
        repository.save(current);
        assertThat(sessions.read(session).opening().audioAvailable()).isFalse();
        assertCode(() -> sessions.openingAudio(session), "MEDIA_UNAVAILABLE");
        sessions.delete(session);
        assertCode(() -> sessions.openingAudio(session), "SESSION_NOT_FOUND");
        org.mockito.Mockito.verify(media).resolve("private-opening");
    }

    @Test
    void restartedRuntimeFencesPreviousLeaseAndKeepsThePinnedOpening() {
        publish(TOPIC, "Cafe");
        authorize(LEARNER, UserRole.USER);
        var request = new Create(TOPIC, UUID.randomUUID());
        String session = sessions.create(request).session().sessionId();
        runtime.completeOpening(session, new SpeakingOpeningVo("Hello", null, SpeakingAudioStatus.TEXT_READY, null));
        var firstLease = runtime.consumeTicket(session, sessions.ticket(session, 1).ticket());
        runtime.admit(firstLease, UUID.randomUUID());

        var restarted = new fit.iuh.engolearn.speaking.service.impl.SpeakingSessionServiceImpl(
                repository, ownerSlots, turns, archive, access, quota, media, users, settings, providerProperties, clock);
        restarted.recover();
        assertCode(() -> restarted.state(firstLease), "CONTROLLER_CHANGED");
        var newLease = restarted.consumeTicket(session, restarted.ticket(session, 1).ticket());
        assertThat(restarted.state(newLease).opening().reply()).isEqualTo("Hello");
        assertThat(restarted.create(request).created()).isFalse();
        assertThat(restarted.turns(session, 0, 20).items()).singleElement()
                .satisfies(turn -> assertThat(turn.reason()).isEqualTo("SYSTEM_INTERRUPTED"));
        assertThat(restarted.state(newLease).finalizedTurnCount()).isZero();
        assertThat(mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(),
                fit.iuh.engolearn.topic.model.UserTopicItemProgress.class)).isZero();
    }

    private void publish(SpeakingScopeDto scope, String description) {
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        profiles.save(scope, 0, profile(scope, description));
        profiles.publish(scope, 1);
    }

    private TopicSpeakingProfileDto profile(SpeakingScopeDto scope, String description) {
        return new TopicSpeakingProfileDto(true, CefrLevel.A1, description, "Barista", "Customer",
                List.of(), profiles.read(scope).resources().stream().map(resource -> resource.id()).toList(), "Hello", null);
    }

    private void account(String id, UserRole role) {
        mongoTemplate.insert(new User(id, id + "@example.test", "unused", role, false));
        mongoTemplate.insert(fit.iuh.engolearn.user.model.UserProfile.builder().id(id).cefrLevel(CefrLevel.A1).build());
        mongoTemplate.insert(RefreshSession.builder().sessionId("auth-" + id)
                .accountId(id).expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private Cookie cookie(String id) {
        return new Cookie("ACCESS_TOKEN", tokens.create(id, "auth-" + id, "access", 900));
    }

    private static void authorize(String id, UserRole role) {
        var user = new AuthenticatedUserPrincipal(id, id + "@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static <T> T asLearner(Supplier<T> operation) {
        authorize(LEARNER, UserRole.USER);
        try {
            return operation.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Object tryResume(String id, Resume request) {
        return asLearner(() -> {
            try {
                return sessions.resume(id, request);
            } catch (SpeakingException error) {
                return error.code();
            }
        });
    }

    private Object consume(String id, String token) {
        try {
            return runtime.consumeTicket(id, token);
        } catch (SpeakingException error) {
            return error.code();
        }
    }

    private static <T> List<T> simultaneous(Callable<T> first, Callable<T> second) throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> { gate.await(); return first.call(); });
            var right = pool.submit(() -> { gate.await(); return second.call(); });
            gate.countDown();
            return List.of(left.get(), right.get());
        }
    }

    private static void assertCodeTopic(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(fit.iuh.engolearn.topic.exception.TopicSpeakingProfileException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SpeakingException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
