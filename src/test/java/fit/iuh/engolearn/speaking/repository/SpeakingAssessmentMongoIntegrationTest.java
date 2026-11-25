package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.SpeakingMongoIndexes;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.*;
import fit.iuh.engolearn.speaking.service.FinalizedSpeakingAudio;
import fit.iuh.engolearn.speaking.service.SpeakingAssessment;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.utils.SpeakingAcousticPolicy;
import fit.iuh.engolearn.speaking.utils.SpeakingLanguageRubric;
import fit.iuh.engolearn.speaking.utils.SpeakingWav;
import fit.iuh.engolearn.speaking.utils.port.*;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@Import(SpeakingAssessmentMongoIntegrationTest.TestClock.class)
@TestPropertySource(properties = {"engolearn.speaking.recovery.enabled=false",
        "engolearn.speaking.assessment.audio-queue-capacity=4",
        "engolearn.security.jwt.secret=speaking-assessment-db-only-integration-secret"})
class SpeakingAssessmentMongoIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String OWNER = "assessment-owner";
    private static final String OTHER = "assessment-other";
    private static final String BASE = "/api/v1/speaking/sessions";
    private static final byte[] PCM = SpeakingAssessmentAudioFixture.voicedPcm();
    private static final String RUBRIC = """
            {"grammar":{"accuracy":{"status":"ASSESSED","level":5,"reasonVi":"Câu rõ nghĩa."},
            "range":{"status":"ASSESSED","level":3,"reasonVi":"Mẫu câu khá đa dạng."},"examples":[]},
            "vocabulary":{"accuracy":{"status":"ASSESSED","level":4,"reasonVi":"Từ phù hợp."},
            "range":{"status":"ASSESSED","level":3,"reasonVi":"Có thể mở rộng vốn từ."},"examples":[]},
            "feedback":{"strengthsVi":["Bạn diễn đạt rõ ý."],"improvementsVi":[],"recommendationsVi":[]}}
            """;

    @Autowired private ObjectMapper mapper;
    @Autowired private JwtTokenService tokens;
    @Autowired private SpeakingSessionRepository sessions;
    @Autowired private SpeakingAssessmentRepository repository;
    @Autowired private SpeakingAssessment worker;
    @Autowired private SpeakingRuntime runtime;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private AdjustableClock clock;
    @MockitoBean private AcousticAssessmentPort acoustic;
    @MockitoBean private LanguageAssessmentPort language;
    @MockitoBean private LearnerAssessmentAudioPort retainedAudio;
    @MockitoBean private MediaManagement media;

    @BeforeEach
    void fixtures() {
        clock.time.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        SpeakingMongoIndexes.initialize(mongoTemplate);
        account(OWNER);
        account(OTHER);
    }

    @Test
    void recoversMissingJobsAndInterruptedClaimsAndPublishesOneOwnerOnlyResult() throws Exception {
        var session = session(5, null);
        var history = new ArrayList<SpeakingTurnVo>();
        for (int i = 1; i <= 5; i++) history.add(archived(session, i, i <= 3, i != 4));
        var orphan = history.getFirst();
        repository.register(session.getId(), orphan.getTurnId(), 1, clock.instant(), "azure-v2.1", "en-US");
        repository.claim(session.getId(), orphan.getTurnId(), "crashed-process", "old-claim",
                clock.instant(), clock.instant().plusSeconds(15), 2, "azure-v2.1");
        var mediaUri = URI.create("https://media.test/retained-speaking-fixture");
        when(media.resolve(anyString())).thenAnswer(invocation -> new MediaDeliveryDto(mediaUri, clock.instant().plusSeconds(50), "audio/wav"));
        when(retainedAudio.read(eq(mediaUri), anyInt(), any())).thenReturn(SpeakingWav.learner(PCM));
        var attempts = new AtomicInteger();
        when(acoustic.assess(any(), any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 2) throw new AcousticAssessmentPort.Failure("AUDIO_PROVIDER_TIMEOUT", true);
            return acoustic(60d, 0d);
        });
        when(language.assess(anyString(), anyString(), anyList(), anyString(), any())).thenReturn(RUBRIC);

        assertThat(data(request("GET", endpoint(session, "/assessment"), OWNER, null)).path("status").asString()).isEqualTo("PENDING");
        assertThat(data(end(session)).path("status").asString()).isEqualTo("ENDED");
        var finalizing = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
        assertThat(finalizing.path("status").asString()).isEqualTo("FINALIZING");
        assertThat(finalizing.path("criteria").path("fluency").path("score").isNull()).isTrue();
        await(() -> terminal(session));
        var result = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
        assertThat(result.path("status").asString()).isEqualTo("READY");
        var criteria = result.path("criteria");
        assertThat(criteria.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder(
                "pronunciation", "fluency", "grammar", "vocabulary");
        assertThat(criteria.path("pronunciation").path("score").asDouble()).isEqualTo(60);
        assertThat(criteria.path("fluency").path("score").asDouble()).isZero();
        assertThat(criteria.path("grammar").path("score").asDouble()).isEqualTo(4);
        assertThat(criteria.path("vocabulary").path("score").asDouble()).isEqualTo(3.5);
        assertThat(criteria.path("pronunciation").path("wordCount").asInt()).isEqualTo(24);
        assertThat(repository.samples(session.getId())).hasSize(5);
        assertThat(repository.samples(session.getId())).filteredOn(sample -> orphan.getTurnId().equals(sample.getId()))
                .singleElement().satisfies(sample -> assertThat(sample.getPolicyVersion()).isEqualTo(SpeakingAcousticPolicy.VERSION));
        assertThat(repository.samples(session.getId())).filteredOn(sample -> "AUDIO_MEDIA_UNAVAILABLE".equals(sample.getReason())).hasSize(2);
        assertThat(attempts.get()).isEqualTo(4);
        verify(retainedAudio, times(3)).read(eq(mediaUri), anyInt(), any());
        verify(language).assess(anyString(), argThat(index -> index.contains("displayedHint")
                        && !index.contains("memoryDelta") && !index.contains("previousScore")),
                argThat(messages -> messages.size() == 9
                        && messages.getFirst().text().startsWith("Yesterday")
                        && messages.getLast().text().equals("Delivered reply 5")
                        && messages.stream().noneMatch(message -> message.text().equals("Undelivered reply 4"))),
                eq(SpeakingLanguageRubric.SCHEMA), any());
        assertThat(data(end(session)).path("assessmentStatus").asString()).isEqualTo("READY");
        assertThat(data(request("GET", BASE, OWNER, null)).path("items").get(0).path("assessmentStatus").asString()).isEqualTo("READY");
        assertThat(data(request("GET", endpoint(session, "/assessment"), OWNER, null))).isEqualTo(result);
        assertThat(request("GET", endpoint(session, "/assessment"), OTHER, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", endpoint(session, "/assessment"), null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void recoveredLongPcmReachesProviderOnceAndStoresOnlyRecognizedEvidence() throws Exception {
        var session = session(1, null);
        var turn = archived(session, 1, true, true);
        var longPcm = SpeakingAssessmentAudioFixture.voicedPcm(120_000);
        var wav = SpeakingWav.learner(longPcm);
        var mediaUri = URI.create("https://media.test/long-recovered-acoustic-fixture");
        when(media.resolve("audio-1")).thenReturn(new MediaDeliveryDto(mediaUri, clock.instant().plusSeconds(50), "audio/wav"));
        when(retainedAudio.read(eq(mediaUri), eq(wav.length), any())).thenReturn(wav);
        when(acoustic.assess(any(), any())).thenReturn(new AcousticAssessmentPort.Result(81d, 76d, 85, List.of(), 84_000, 70_000));

        data(end(session));
        await(() -> terminal(session));

        verify(acoustic).assess(argThat(bytes -> java.util.Arrays.equals(bytes, longPcm)), any());
        assertThat(repository.samples(session.getId())).singleElement().satisfies(sample -> {
            assertThat(sample.getId()).isEqualTo(turn.getTurnId());
            assertThat(sample.getDurationMs()).isEqualTo(120_000);
            assertThat(sample.getAssessedSpeechDurationMs()).isEqualTo(84_000L);
            assertThat(sample.getFluencyEvidenceDurationMs()).isEqualTo(70_000L);
        });
        var result = data(request("GET", endpoint(session, "/assessment"), OWNER, null)).path("criteria");
        assertThat(result.path("pronunciation").path("sampleCount").asInt()).isEqualTo(1);
        assertThat(result.path("pronunciation").path("speechDurationMs").asLong()).isEqualTo(84_000L);
        assertThat(result.path("fluency").path("sampleCount").asInt()).isEqualTo(1);
        assertThat(result.path("fluency").path("speechDurationMs").asLong()).isEqualTo(70_000L);
        assertThat(result.path("fluency").path("score").isNull()).isTrue();
    }

    @Test
    void closingSettlesTheLastAdmittedTurnAndPublishesLanguageDespiteAudioQuotaFailure() throws Exception {
        var active = turn(5, false, false);
        active.setStatus(SpeakingTurnStatus.TRANSCRIBING);
        active.setWorkToken("admitted");
        active.setWorkDeadline(clock.instant().plusSeconds(60));
        active.setTranscript(null);
        var session = session(5, active);
        for (int i = 1; i <= 4; i++) archived(session, i, false, true);
        when(acoustic.assess(any(), any())).thenThrow(new AcousticAssessmentPort.Failure("AUDIO_PROVIDER_QUOTA", false));
        when(language.assess(anyString(), anyString(), anyList(), anyString(), any())).thenReturn(RUBRIC);
        events.publishEvent(new FinalizedSpeakingAudio(session.getId(), active.getTurnId(), 5, null, PCM));
        assertThat(data(end(session)).path("status").asString()).isEqualTo("CLOSING");
        var work = new SpeakingRuntime.TurnWork(session.getId(), active.getTurnId(), "admitted");
        runtime.transcript(work, transcript(5));
        runtime.settle(work, SpeakingTurnStatus.FAILED, "CONVERSATION_UNAVAILABLE", null, null, null);
        await(() -> terminal(session));
        var result = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
        assertThat(result.path("status").asString()).isEqualTo("PARTIAL");
        assertThat(result.path("criteria").path("pronunciation").path("status").asString()).isEqualTo("UNAVAILABLE");
        assertThat(result.path("criteria").path("pronunciation").path("score").isNull()).isTrue();
        assertThat(result.path("criteria").path("grammar").path("sampleCount").asInt()).isEqualTo(5);
        assertThat(repository.samples(session.getId())).filteredOn(sample -> "AUDIO_PROVIDER_QUOTA".equals(sample.getReason())).hasSize(1);
        verify(acoustic, times(1)).assess(any(), any());
        verify(language).assess(anyString(), anyString(), argThat(messages -> messages.getLast().text().equals(transcript(5))),
                eq(SpeakingLanguageRubric.SCHEMA), any());
    }

    @Test
    void resumesAfterTheLanguageResultWasAlreadyPersisted() throws Exception {
        var session = session(4, null);
        for (int i = 1; i <= 3; i++) archived(session, i, true, true);
        var incomplete = turn(4, true, true);
        incomplete.setCaptureComplete(false);
        mongoTemplate.insert(SpeakingTurn.builder().id(incomplete.getTurnId()).sessionId(session.getId()).value(incomplete).build());
        var mediaUri = URI.create("https://media.test/recovered-acoustic-fixture");
        when(media.resolve(anyString())).thenAnswer(invocation -> new MediaDeliveryDto(mediaUri, clock.instant().plusSeconds(50), "audio/wav"));
        when(retainedAudio.read(eq(mediaUri), anyInt(), any())).thenReturn(SpeakingWav.learner(PCM));
        when(acoustic.assess(any(), any())).thenReturn(acoustic(80d, 70d));
        data(end(session));
        assertThat(repository.startLanguage(session.getId(), clock.instant().plusSeconds(30))).isTrue();
        assertThat(repository.storeLanguage(session.getId(), SpeakingLanguageRubric.missing(
                SpeakingCriterionStatus.UNAVAILABLE, "LANGUAGE_PROVIDER_QUOTA", 5, 60))).isTrue();

        await(() -> terminal(session));
        var result = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
        assertThat(result.path("status").asString()).isEqualTo("PARTIAL");
        assertThat(result.path("criteria").path("pronunciation").path("score").asDouble()).isEqualTo(80);
        assertThat(result.path("criteria").path("fluency").path("score").asDouble()).isEqualTo(70);
        assertThat(result.path("criteria").path("grammar").path("reason").asString()).isEqualTo("LANGUAGE_PROVIDER_QUOTA");
        assertThat(sessions.findById(session.getId()).orElseThrow().getLanguageAssessment()).isNull();
        assertThat(repository.samples(session.getId())).filteredOn(sample -> "AUDIO_INCOMPLETE".equals(sample.getReason())).hasSize(1);
        verify(media, never()).resolve("audio-4");
        verifyNoInteractions(language);
    }

    @Test
    void boundedQueueAndDeadlineFenceLateSamplesAndDeletedEvidence() throws Exception {
        var session = session(6, null);
        var history = new ArrayList<SpeakingTurnVo>();
        for (int i = 1; i <= 6; i++) {
            var turn = archived(session, i, false, true);
            history.add(turn);
        }
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(acoustic.assess(any(), any())).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
            return acoustic(100d, 100d);
        });
        when(language.assess(anyString(), anyString(), anyList(), anyString(), any()))
                .thenThrow(new LanguageAssessmentPort.Failure("LANGUAGE_PROVIDER_DISABLED"));
        try {
            var first = history.getFirst();
            events.publishEvent(new FinalizedSpeakingAudio(session.getId(), first.getTurnId(), 1, null, PCM));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (var turn : history.subList(1, history.size())) {
                events.publishEvent(new FinalizedSpeakingAudio(session.getId(), turn.getTurnId(), turn.getSequence(), null, PCM));
            }
            assertThat(repository.samples(session.getId())).filteredOn(sample -> "AUDIO_QUEUE_FULL".equals(sample.getReason())).hasSize(1);
            data(end(session));
            worker.recover();
            await(() -> sessions.findById(session.getId()).orElseThrow().getLanguageAssessment() != null);
            clock.time.updateAndGet(time -> time.plusSeconds(31));
            await(() -> terminal(session));
            JsonNode result = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
            assertThat(result.path("status").asString()).isEqualTo("UNAVAILABLE");
            assertThat(result.path("criteria").path("grammar").path("reason").asString()).isEqualTo("LANGUAGE_PROVIDER_DISABLED");
            assertThat(result.path("criteria").path("fluency").path("score").isNull()).isTrue();
            release.countDown();
            assertThat(data(request("GET", endpoint(session, "/assessment"), OWNER, null))).isEqualTo(result);
            assertThat(request("DELETE", endpoint(session, ""), OWNER, null).statusCode()).isEqualTo(204);
            events.publishEvent(new FinalizedSpeakingAudio(session.getId(), first.getTurnId(), 1, null, PCM));
            worker.recover();
            assertThat(request("GET", endpoint(session, "/assessment"), OWNER, null).statusCode()).isEqualTo(404);
            assertThat(sessions.findById(session.getId()).orElseThrow().getDeletedAt()).isNotNull();
        } finally {
            release.countDown();
        }
    }

    @Test
    void cutoffDiscardsUnsettledWorkAndSeparatesInsufficientEvidenceFromTechnicalFailure() throws Exception {
        var active = turn(1, false, false);
        active.setStatus(SpeakingTurnStatus.TRANSCRIBING);
        active.setWorkToken("late");
        active.setWorkDeadline(clock.instant().plusSeconds(60));
        active.setTranscript(null);
        var session = session(1, active);
        assertThat(data(end(session)).path("status").asString()).isEqualTo("CLOSING");
        clock.time.updateAndGet(time -> time.plusSeconds(16));
        assertThatThrownBy(() -> runtime.transcript(new SpeakingRuntime.TurnWork(session.getId(), active.getTurnId(), "late"),
                transcript(1))).isInstanceOf(fit.iuh.engolearn.speaking.exception.SpeakingException.class);
        runtime.recover();
        await(() -> terminal(session));
        var result = data(request("GET", endpoint(session, "/assessment"), OWNER, null));
        assertThat(result.path("status").asString()).isEqualTo("UNAVAILABLE");
        assertThat(result.path("criteria").path("grammar").path("status").asString()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.path("criteria").path("pronunciation").path("status").asString()).isEqualTo("UNAVAILABLE");
        verifyNoInteractions(language, acoustic);
    }

    private boolean terminal(SpeakingSession session) {
        return sessions.findById(session.getId()).orElseThrow().getAssessment() != null;
    }

    private SpeakingSession session(int last, SpeakingTurnVo active) {
        var now = clock.instant();
        return sessions.insert(SpeakingSession.builder().id(UUID.randomUUID().toString()).ownerId(OWNER).unfinishedOwner(OWNER)
                .clientRequestId(UUID.randomUUID().toString()).controllerEpoch(1)
                .snapshot(SpeakingSnapshotVo.builder().scopeType(SpeakingScopeType.TOPIC).topicId("cafe")
                        .title("Cafe").topicTitle("Cafe").level(CefrLevel.A1).description("Order a drink").aiRole("Barista")
                        .learnerRole("Customer").build())
                .limits(new SpeakingLimitsVo(30, 1_200_000, 120_000, 15_000)).createdAt(now.minusSeconds(90))
                .startedAt(now.minusSeconds(89)).status(SpeakingSessionStatus.ACTIVE)
                .assessmentStatus(SpeakingAssessmentStatus.PENDING).lastTurnSequence(last)
                .finalizedTurnCount(last - (active == null ? 0 : 1)).activeTurn(active).build());
    }

    private SpeakingTurnVo archived(SpeakingSession session, int n, boolean retained, boolean delivered) {
        var value = turn(n, retained, delivered);
        mongoTemplate.insert(SpeakingTurn.builder().id(value.getTurnId()).sessionId(session.getId()).value(value).build());
        return value;
    }

    private SpeakingTurnVo turn(int n, boolean retained, boolean delivered) {
        return SpeakingTurnVo.builder().turnId(UUID.randomUUID().toString()).clientTurnId(UUID.randomUUID().toString())
                .sequence(n).revision(2).status(SpeakingTurnStatus.COMMITTED).transcript(transcript(n))
                .reply((delivered ? "Delivered" : "Undelivered") + " reply " + n)
                .hint(new SpeakingHintVo("A cup of tea, please.", "Gợi ý gọi trà."))
                .audioStatus(SpeakingAudioStatus.TEXT_READY).sealedAt(clock.instant().minusSeconds(5))
                .learnerAudio(retained ? new SpeakingMediaVo("audio-" + n, clock.instant().minusSeconds(4)) : null)
                .replyDeliveredAt(delivered ? clock.instant().minusSeconds(2) : null).build();
    }

    private String transcript(int n) {
        return "Yesterday I had some coffee before I went home with my friends " + n;
    }

    private static AcousticAssessmentPort.Result acoustic(double pronunciation, double fluency) {
        return new AcousticAssessmentPort.Result(pronunciation, fluency, 8,
                List.of(new SpeakingWordAssessmentVo("coffee", 50d, "Mispronunciation")), 8_000, 7_000);
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            worker.recover();
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private HttpResponse<String> end(SpeakingSession session) throws Exception {
        return request("POST", endpoint(session, "/end"), OWNER, "{\"controllerEpoch\":1}");
    }

    private String endpoint(SpeakingSession session, String suffix) {
        return BASE + "/" + session.getId() + suffix;
    }

    private HttpResponse<String> request(String method, String path, String account, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(8));
        if (account != null) request.header("Cookie", "ACCESS_TOKEN=" + tokens.create(account, "auth-" + account, "access", 900));
        if (body != null) request.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return mapper.readTree(response.body()).path("data");
    }

    private void account(String id) {
        mongoTemplate.insert(new User(id, id + "@example.test", "unused", UserRole.USER, false));
        mongoTemplate.insert(RefreshSession.builder().sessionId("auth-" + id)
                .accountId(id).expiresAt(Instant.now().plusSeconds(900)).build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean @Primary AdjustableClock speakingFixtureClock() { return new AdjustableClock(); }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.now());

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return time.get(); }
    }
}
