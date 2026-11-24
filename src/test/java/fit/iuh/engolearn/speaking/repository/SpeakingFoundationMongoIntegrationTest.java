package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.SpeakingMongoIndexes;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.service.impl.QuotaServiceImpl;
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
import fit.iuh.engolearn.speaking.service.SpeakingSessions;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.topic.contract.TopicSpeakingAccess;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.service.TopicSpeakingProfileService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                    .andExpect(jsonPath("$.data.resources[0].id").value("juice"));
            mvc.perform(put(path).cookie(cookie(AUTHOR)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"revision\":0,\"profile\":{\"enabled\":false,\"selectedResourceIds\":[\"unreleased\"]}}"))
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
        assertThatThrownBy(() -> profiles.save(TOPIC, 2, profile("Denied")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> profiles.publish(ITEM, 2)).isInstanceOf(AccessDeniedException.class);
        assertThat(mongoTemplate.findById(TOPIC_ID, Topic.class).getRevision()).isEqualTo(4L);
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
        profiles.save(TOPIC, 2, profile("Updated"));
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
    void invalidEnabledProfilesFailPublishAndReadinessAtBothScopes() throws Exception {
        authorize(AUTHOR, UserRole.CONTENT_MANAGER);
        var invalid = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Description", "AI", "Learner",
                List.of(), List.of("unreleased-vocab"), "Hello", null);
        for (var scope : List.of(TOPIC, ITEM)) {
            profiles.save(scope, 0, invalid);
            assertCodeTopic(() -> profiles.publish(scope, 1), "INVALID_PROFILE");
        }
        mvc.perform(get("/api/management/topics/" + TOPIC_ID + "/readiness").cookie(cookie(AUTHOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violations[?(@.code == 'INVALID_RESOURCE')].path")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("topic.speakingProfile.selectedResourceIds",
                                "items[" + ITEM_ID + "].speakingProfile.selectedResourceIds")));
        assertThat(access.resolve(LEARNER, new SpeakingScopeDto(SpeakingScopeType.TOPIC_ITEM, "another-topic", ITEM_ID))
                .reason().name()).isEqualTo("SCOPE_FORBIDDEN");
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
                repository, ownerSlots, turns, archive, access, quota, media, users, settings, clock);
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
        profiles.save(scope, 0, profile(description));
        profiles.publish(scope, 1);
    }

    private static TopicSpeakingProfileDto profile(String description) {
        return new TopicSpeakingProfileDto(true, CefrLevel.A1, description, "Barista", "Customer",
                List.of(), List.of("juice"), "Hello", null);
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
