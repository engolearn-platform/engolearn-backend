package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.SpeakingMongoIndexes;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.service.impl.QuotaServiceImpl;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.service.SpeakingQuota;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.service.FinalizedSpeakingAudio;
import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Prompt;
import fit.iuh.engolearn.speaking.utils.SpeakingWav;
import fit.iuh.engolearn.speaking.utils.port.*;
import fit.iuh.engolearn.topic.service.TopicSpeakingProfileService;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@Import(SpeakingRealtimeMongoIntegrationTest.TestEvents.class)
@TestPropertySource(properties = {"engolearn.speaking.recovery.enabled=false",
        "engolearn.security.jwt.secret=speaking-realtime-db-only-integration-secret"})
class SpeakingRealtimeMongoIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/v1/speaking";
    private static final String ORIGIN = "http://localhost:3000";
    private static final String OWNER = "realtime-owner";
    private static final String AUTHOR = "realtime-author";
    private static final String TOPIC_ID = "66fa4639f05f7400cafe1000";
    private static final String OTHER = "realtime-other";
    private static final byte[] WAV = SpeakingWav.learner(new byte[9600]);
    private static final byte[] VOICE = voice();
    private static final SpeakingScopeDto SCOPE = new SpeakingScopeDto(SpeakingScopeType.TOPIC, TOPIC_ID, null);
    private static final String REPLY = """
            {"reply":"One juice, coming right up!","hint":{"exampleEn":"Thanks!","explanationVi":"Cảm ơn."},
             "memoryDelta":[{"operation":"UPSERT","target":"FACT","key":"drink","value":"Orange juice"}]}
            """;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final List<UsageCommandDto> quotaCalls = new CopyOnWriteArrayList<>();
    private final Map<String, SpeechStreamPort.Listener> listeners = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sentPcm = new ConcurrentHashMap<>();
    private final AtomicReference<String> transcript = new AtomicReference<>("Yes!");
    private final AtomicReference<String> answer = new AtomicReference<>(REPLY);
    private final AtomicReference<String> opening = new AtomicReference<>("""
            {"reply":"Good morning! What can I get you?","hint":{"exampleEn":"Juice, please.","explanationVi":"Gọi món."}}
            """);
    private final AtomicReference<CompletableFuture<String>> openingGate = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<String>> responseGate = new AtomicReference<>();
    private final AtomicInteger generationCalls = new AtomicInteger();
    private final AtomicInteger quotaFailures = new AtomicInteger();
    private final HttpClient http = HttpClient.newHttpClient();
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtTokenService tokens;
    @Autowired private SpeakingTurnRepository turns;
    @Autowired private SpeakingSessionRepository sessions;
    @Autowired private SpeakingQuota speakingQuota;
    @Autowired private TopicSpeakingProfileService profiles;
    @Autowired private SpeakingRuntime runtime;
    @Autowired private CapturedAudio captured;
    @MockitoBean private MediaManagement media;
    @MockitoBean private SpeechStreamPort stt;
    @MockitoBean private ConversationGenerationPort gemini;
    @MockitoBean private SpeechSynthesisPort cartesia;
    @MockitoSpyBean private QuotaServiceImpl quota;

    @BeforeEach
    void fixture() {
        SpeakingMongoIndexes.initialize(mongoTemplate);
        captured.events.clear();
        account(OWNER, UserRole.USER);
        account(OTHER, UserRole.USER);
        account(AUTHOR, UserRole.CONTENT_MANAGER);
        mongoTemplate.insert(Topic.builder().id(TOPIC_ID).latestPublishedVersion("cafe-realtime-v1")
                .titleEn("Cafe").cefrLevel(CefrLevel.A1).revision(1L).build());
        mongoTemplate.insert(TopicVersion.builder().id("cafe-realtime-v1").topicId(TOPIC_ID)
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of()).build());
        var profile = new TopicSpeakingProfileDto(true, CefrLevel.A1, "Order at the cafe", "Barista", "Customer",
                List.of("drinks"), List.of(), "Welcome to the cafe!",
                new TopicSpeakingProfileDto.Hint("Can I have water?", "Hỏi xin nước."));
        var author = new AuthenticatedUserPrincipal(AUTHOR, AUTHOR + "@example.test", UserRole.CONTENT_MANAGER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(author, null,
                author.getAuthorities()));
        try {
            profiles.save(SCOPE, 0, profile);
            profiles.publish(SCOPE, 1);
        } finally {
            SecurityContextHolder.clearContext();
        }
        doReturn(new AvailabilityResultDto("SPEAKING_DURATION", 0, 3600, 3600, "SECOND", "DAILY",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), "ACTIVE"))
                .when(quota).checkAvailability(OWNER, "SPEAKING_DURATION");
        doAnswer(call -> {
            quotaCalls.add(call.getArgument(0));
            if (quotaFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) throw new IllegalStateException("quota offline");
            return null;
        }).when(quota).recordUsage(any());
        when(media.upload(any())).thenAnswer(call -> {
            var input = call.getArgument(0, fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto.class);
            assertThat(input.accessPolicy().name()).isEqualTo("PROTECTED");
            byte[] bytes = input.content().readAllBytes();
            assertThat(SpeakingWav.durationMs(bytes)).isPositive();
            return new MediaUploadResultDto(input.originalFilename(), "audio/wav", bytes.length, input.accessPolicy());
        });
        when(media.resolve(anyString())).thenReturn(new MediaDeliveryDto(URI.create("https://media.test/private"),
                Instant.now().plusSeconds(50), "audio/wav"));
        when(stt.open(anyString(), any())).thenAnswer(call -> {
            listeners.put(call.getArgument(0), call.getArgument(1));
            return CompletableFuture.completedFuture(null);
        });
        doAnswer(call -> {
            String id = call.getArgument(0);
            sentPcm.computeIfAbsent(id, ignored -> new AtomicInteger()).addAndGet(call.<byte[]>getArgument(1).length);
            listeners.get(id).onInterim("Ye");
            return null;
        }).when(stt).send(anyString(), any());
        when(stt.endTurn(anyString())).thenAnswer(call -> transcript.get() == null
                ? CompletableFuture.failedFuture(new IllegalStateException("STT offline"))
                : CompletableFuture.completedFuture(transcript.get()));
        when(gemini.generate(any())).thenAnswer(call -> {
            Prompt prompt = call.getArgument(0);
            if (prompt.opening()) {
                var gate = openingGate.get();
                return gate == null ? opening.get() : gate.get(10, TimeUnit.SECONDS);
            }
            generationCalls.incrementAndGet();
            var gate = responseGate.get();
            return gate == null ? answer.get() : gate.get(10, TimeUnit.SECONDS);
        });
        when(cartesia.synthesize(anyString())).thenReturn(WAV);
    }

    @AfterEach
    void closeSockets() {
        sockets.forEach(socket -> {
            try {
                socket.closeNormally();
            } catch (Exception closed) {
                socket.socket.abort();
            }
        });
    }

    @Test
    void realUpgradeEnforcesTicketAndOriginAndDeliversDurableTurnAndWav() throws Exception {
        String id = create();
        assertThat(request("GET", BASE + "/sessions/" + id, null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", BASE + "/sessions/" + id + "/socket-ticket", OTHER,
                "{\"controllerEpoch\":1}").statusCode()).isEqualTo(403);

        String ticket = ticket(id, 1);
        assertHandshakeStatus(id, "", ORIGIN, 401);
        assertHandshakeStatus(id, "?ticket=" + ticket, "https://cors-denied.test", 403);
        Socket connection = socket(id, ticket);
        assertHandshakeStatus(id, "?ticket=" + ticket, ORIGIN, 401);
        ready(id, "Good morning! What can I get you?");
        assertThat(data(request("GET", BASE + "/sessions/" + id, OWNER, null)).path("finalizedTurnCount").asInt()).isZero();

        connection.binary(frame(UUID.randomUUID(), 0, VOICE));
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("STALE_TURN");
        connection.socket.sendText("not json", true).join();
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("INVALID_FRAME");
        connection.socket.sendText("x".repeat(16 * 1024 + 1), true).join();
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("FRAME_TOO_LARGE");

        String clientId = UUID.randomUUID().toString();
        String requestId = connection.command("START_TURN", null, Map.of("clientTurnId", clientId));
        JsonNode accepted = connection.next("TURN_ACCEPTED");
        assertThat(accepted.path("requestId").asString()).isEqualTo(requestId);
        assertThat(accepted.path("payload").path("clientTurnId").asString()).isEqualTo(clientId);
        UUID turn = UUID.fromString(accepted.path("turnId").asString());
        connection.binary(frame(turn, 1, VOICE));
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("INVALID_FRAME");
        connection.binary(frame(turn, 0, VOICE));
        assertThat(connection.next("TRANSCRIPT_PARTIAL").path("turnId").asString()).isEqualTo(turn.toString());
        connection.binary(frame(turn, 0, VOICE));
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("INVALID_FRAME");

        connection.command("END_TURN", turn, Map.of());
        assertThat(connection.next("TURN_PROCESSING").path("payload").path("status").asString()).isEqualTo("TRANSCRIBING");
        assertThat(connection.next("TRANSCRIPT_FINAL").path("payload").path("transcript").asString()).isEqualTo("Yes!");
        JsonNode result = connection.next("TURN_RESULT");
        assertThat(result.path("payload").path("reply").asString()).isEqualTo("One juice, coming right up!");
        assertThat(result.path("payload").has("memoryDelta")).isFalse();
        assertThat(keys(result)).containsExactlyInAnyOrder("type", "sessionId", "turnId", "sequence", "payload");
        var protocolFixture = Path.of("..", "docs", "system-design", "speaking-v2-protocol.fixture.json");
        if (Files.isRegularFile(protocolFixture)) {
            var shared = mapper.readTree(Files.readString(protocolFixture)).path("ws");
            assertThat(keys(result)).containsExactlyInAnyOrderElementsOf(keys(shared.path("result")));
            assertThat(keys(result.path("payload"))).containsExactlyInAnyOrderElementsOf(keys(shared.path("result").path("payload")));
            assertThat(keys(accepted)).containsExactlyInAnyOrderElementsOf(keys(shared.path("accepted")));
        }
        assertThat(turns.findById(turn.toString()).orElseThrow().getValue().getTranscript()).isEqualTo("Yes!");
        assertThat(sessions.findById(id).orElseThrow().getMemory().getFacts()).containsEntry("drink", "Orange juice");
        connection.next("TTS_COMPLETED");
        var audio = connection.audioFor(turn);
        assertThat(audio).startsWith((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
        awaitCondition(() -> !quotaCalls.isEmpty());
        assertThat(sentPcm.get(turn.toString()).get()).isEqualTo(VOICE.length);
        assertThat(generationCalls.get()).isEqualTo(1);
        var stored = data(request("GET", BASE + "/sessions/" + id + "/turns", OWNER, null)).path("items").path(0);
        assertThat(stored.path("replyDeliveredAt").isNull()).isFalse();
        assertThat(stored.path("assistantAudioAvailable").asBoolean()).isTrue();
        assertThat(request("GET", BASE + "/sessions/" + id + "/turns/" + turn + "/audio/LEARNER_RECORDING",
                OWNER, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", BASE + "/sessions/" + id + "/turns/" + turn + "/audio/LEARNER_RECORDING",
                OTHER, null).statusCode()).isEqualTo(403);
        connection.binary(frame(turn, 1, VOICE));
        assertThat(connection.next("ERROR").path("payload").path("code").asString()).isEqualTo("STALE_TURN");
    }

    @Test
    void fallbackOpeningEmptyAndUnavailableSttKeepIndependentTurnHistory() throws Exception {
        opening.set("not json");
        String id = create();
        ready(id, "Welcome to the cafe!");
        var connection = socket(id, ticket(id, 1));
        assertThat(connection.next("SESSION_STATE").path("payload").path("opening").path("hint").path("exampleEn").asString())
                .isEqualTo("Can I have water?");
        UUID silent = start(connection);
        connection.binary(frame(silent, 0, new byte[3200]));
        connection.command("END_TURN", silent, Map.of());
        assertThat(connection.next("TURN_SKIPPED").path("payload").path("code").asString()).isEqualTo("EMPTY_AUDIO");
        awaitCondition(() -> turns.findById(silent.toString()).map(turn -> turn.isQuotaRecorded()).orElse(false));

        transcript.set(null);
        UUID failed = start(connection);
        connection.binary(frame(failed, 0, VOICE));
        connection.command("END_TURN", failed, Map.of());
        assertThat(connection.next("TURN_FAILED").path("payload").path("code").asString()).isEqualTo("STT_UNAVAILABLE");
        var record = turns.findById(failed.toString()).orElseThrow().getValue();
        assertThat(record.getLearnerAudio()).isNotNull();
        assertThat(record.getTranscript()).isNull();
        var evidence = runtime.finalizedAudioEvidence(id, failed.toString());
        assertThat(evidence.getLearnerAudio().getMediaId()).isEqualTo(failed + ".wav");
        assertThat(captured.events.stream().filter(event -> failed.toString().equals(event.turnId())).findFirst().orElseThrow().pcm())
                .isEqualTo(VOICE);
        assertThat(generationCalls.get()).isZero();
        awaitCondition(() -> quotaCalls.size() == 1);
        assertThat(quotaCalls.getFirst().amount()).isOne();

        transcript.set("...?");
        UUID noise = start(connection);
        connection.binary(frame(noise, 0, VOICE));
        connection.command("END_TURN", noise, Map.of());
        assertThat(connection.next("TURN_SKIPPED").path("payload").path("code").asString()).isEqualTo("EMPTY_AUDIO");
        awaitCondition(() -> turns.findById(noise.toString()).map(turn -> turn.isQuotaRecorded()).orElse(false));

        UUID cancelled = start(connection);
        connection.binary(frame(cancelled, 0, VOICE));
        connection.command("CANCEL_TURN", cancelled, Map.of());
        assertThat(connection.next("TURN_SKIPPED").path("payload").path("code").asString()).isEqualTo("TURN_CANCELLED");
        assertThat(data(request("GET", BASE + "/sessions/" + id, OWNER, null)).path("finalizedTurnCount").asInt()).isEqualTo(3);
        assertThat(quotaCalls).hasSize(1);
    }

    @Test
    void takeoverKeepsSealedGenerationThenRetriesOnceAndReplaysTheSameQuotaCharge() throws Exception {
        String id = create();
        ready(id, "Good morning! What can I get you?");
        Socket old = socket(id, ticket(id, 1));
        var gate = new CompletableFuture<String>();
        responseGate.set(gate);
        UUID turn = start(old);
        old.binary(frame(turn, 0, VOICE));
        old.command("END_TURN", turn, Map.of());
        old.next("TRANSCRIPT_FINAL");
        String resume = "{\"controllerEpoch\":1,\"clientRequestId\":\"" + UUID.randomUUID() + "\"}";
        assertThat(data(request("POST", BASE + "/sessions/" + id + "/resume", OWNER, resume))
                .path("controllerEpoch").asLong()).isEqualTo(2);

        Socket renewed = socket(id, ticket(id, 2));
        JsonNode active = renewed.next("SESSION_STATE").path("payload").path("activeTurn");
        assertThat(active.path("turnId").asString()).isEqualTo(turn.toString());
        assertThat(active.path("status").asString()).isEqualTo("GENERATING");
        gate.complete("{\"reply\":\"\"}");
        JsonNode failed = renewed.next("TURN_FAILED");
        assertThat(failed.path("turnId").asString()).isEqualTo(turn.toString());
        assertThat(failed.has("requestId")).isFalse();
        assertThat(failed.path("payload").path("retryable").asBoolean()).isTrue();
        assertThat(turns.findById(turn.toString()).orElseThrow().getValue().getTranscript()).isEqualTo("Yes!");
        assertThat(quotaCalls).isEmpty();

        responseGate.set(null);
        quotaFailures.set(1);
        doThrow(new IllegalStateException("tts unavailable")).when(cartesia).synthesize("One juice, coming right up!");
        renewed.command("RETRY_TURN", turn, Map.of());
        assertThat(renewed.next("TURN_RESULT").path("turnId").asString()).isEqualTo(turn.toString());
        renewed.next("TTS_FAILED");
        awaitCondition(() -> quotaCalls.size() == 1);
        assertThat(turns.findById(turn.toString()).orElseThrow().getCharge()).isNotNull();
        speakingQuota.recover();
        awaitCondition(() -> turns.findById(turn.toString()).orElseThrow().isQuotaRecorded());
        assertThat(quotaCalls).hasSize(2);
        assertThat(quotaCalls.get(0)).isEqualTo(quotaCalls.get(1));

        renewed.command("RETRY_TURN", turn, Map.of());
        assertThat(renewed.next("ERROR").path("payload").path("code").asString()).isEqualTo("STALE_TURN");
        assertThat(generationCalls.get()).isEqualTo(2);
        assertThat(data(request("GET", BASE + "/sessions/" + id, OWNER, null)).path("finalizedTurnCount").asInt()).isEqualTo(1);
    }

    @Test
    void openingIsDurableAndSameEpochReconnectDiscardsUnsealedAudio() throws Exception {
        var gate = new CompletableFuture<String>();
        openingGate.set(gate);
        String id = create();
        Socket first = socket(id, ticket(id, 1));
        assertThat(first.next("SESSION_STATE").path("payload").path("status").asString()).isEqualTo("INITIALIZING");
        first.command("START_TURN", null, Map.of("clientTurnId", UUID.randomUUID()));
        assertThat(first.next("ERROR").path("payload").path("code").asString()).isEqualTo("OPENING_PENDING");
        gate.complete(opening.get());
        JsonNode open = first.next("OPENING_RESULT");
        assertThat(sessions.findById(id).orElseThrow().getOpening().getReply())
                .isEqualTo(open.path("payload").path("reply").asString());
        first.next("TTS_COMPLETED");
        UUID discarded = start(first);
        first.binary(frame(discarded, 0, VOICE));
        first.next("TRANSCRIPT_PARTIAL");
        first.closeNormally();
        awaitCondition(() -> sessions.findById(id).orElseThrow().getLeaseId() == null);
        Socket reconnected = socket(id, ticket(id, 1));
        JsonNode restored = reconnected.next("SESSION_STATE").path("payload");
        assertThat(restored.path("opening").path("reply").asString()).isEqualTo(open.path("payload").path("reply").asString());
        assertThat(restored.path("controllerEpoch").asLong()).isOne();
        assertThat(restored.path("activeTurn").isNull()).isTrue();
        assertThat(restored.path("finalizedTurnCount").asInt()).isZero();
        var archived = data(request("GET", BASE + "/sessions/" + id + "/turns", OWNER, null)).path("items").path(0);
        assertThat(archived.path("turnId").asString()).isEqualTo(discarded.toString());
        assertThat(archived.path("reason").asString()).isEqualTo("TURN_CANCELLED");
        assertThat(captured.events).isEmpty();
        assertThat(quotaCalls).isEmpty();
        assertThat(generationCalls.get()).isZero();
    }

    @Test
    void mediaFailureDoesNotSuppressFinalizedPcmAndArchivedDeltasReplayIntoNextPrompt() throws Exception {
        doThrow(new IllegalStateException("media offline")).when(media).upload(argThat(input ->
                "speaking-learner".equals(input.purpose())));
        String id = create();
        ready(id, "Good morning! What can I get you?");
        Socket socket = socket(id, ticket(id, 1));
        UUID first = start(socket);
        socket.binary(frame(first, 0, VOICE));
        socket.command("END_TURN", first, Map.of());
        socket.next("TURN_RESULT");
        socket.next("TTS_COMPLETED");
        awaitCondition(() -> turns.findById(first.toString()).map(turn -> turn.isQuotaRecorded()).orElse(false));
        var evidence = runtime.finalizedAudioEvidence(id, first.toString());
        assertThat(evidence.getLearnerAudio()).isNull();
        assertThat(captured.events).singleElement().satisfies(event -> {
            assertThat(event.pcm()).isEqualTo(VOICE);
            assertThat(event.mediaId()).isNull();
        });
        // Remove the cache to simulate recovery with committed deltas still present.
        var session = sessions.findById(id).orElseThrow();
        session.setMemory(null);
        sessions.save(session);
        var following = new AtomicReference<Prompt>();
        doAnswer(call -> {
            following.set(call.getArgument(0));
            return "{\"reply\":\"Here you go.\",\"memoryDelta\":[]}";
        }).when(gemini).generate(any());
        UUID second = start(socket);
        socket.binary(frame(second, 0, VOICE));
        socket.command("END_TURN", second, Map.of());
        socket.next("TURN_RESULT");
        socket.next("TTS_COMPLETED");
        assertThat(following.get().data()).contains("Orange juice");
        assertThat(sessions.findById(id).orElseThrow().getMemory().getProcessedThroughTurn()).isEqualTo(2);
        assertThat(sessions.findById(id).orElseThrow().getMemory().getVersion()).isOne();
    }

    private static List<String> keys(JsonNode object) {
        return object.properties().stream().map(Map.Entry::getKey).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEvents {
        @Bean CapturedAudio capturedAudio() { return new CapturedAudio(); }
    }

    static class CapturedAudio {
        final List<FinalizedSpeakingAudio> events = new CopyOnWriteArrayList<>();
        @EventListener void receive(FinalizedSpeakingAudio event) { events.add(event); }
    }

    private String create() throws Exception {
        return data(request("POST", BASE + "/sessions", OWNER, mapper.writeValueAsString(
                Map.of("scope", SCOPE, "clientRequestId", UUID.randomUUID())))).path("sessionId").asString();
    }

    private void ready(String id, String reply) throws Exception {
        awaitCondition(() -> sessions.findById(id).map(SpeakingSession::getOpening).filter(opening ->
                !opening.getAudioStatus().name().equals("AUDIO_PENDING")).isPresent());
        var session = data(request("GET", BASE + "/sessions/" + id, OWNER, null));
        assertThat(session.path("opening").path("reply").asString()).isEqualTo(reply);
        assertThat(session.path("committedTurnCount").asInt()).isZero();
    }

    private UUID start(Socket socket) {
        socket.command("START_TURN", null, Map.of("clientTurnId", UUID.randomUUID()));
        return UUID.fromString(socket.next("TURN_ACCEPTED").path("turnId").asString());
    }

    private String ticket(String id, long epoch) throws Exception {
        return data(request("POST", BASE + "/sessions/" + id + "/socket-ticket", OWNER, "{\"controllerEpoch\":" + epoch + "}"))
                .path("ticket").asString();
    }

    private HttpResponse<String> request(String method, String path, String account, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(java.time.Duration.ofSeconds(8));
        if (account != null) request.header("Cookie", "ACCESS_TOKEN=" + tokens.create(account, "auth-" + account, "access", 900));
        if (body != null) request.header("Content-Type", "application/json");
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return mapper.readTree(response.body()).path("data");
    }

    private void account(String id, UserRole role) {
        mongoTemplate.insert(new User(id, id + "@example.test", "unused", role, false));
        mongoTemplate.insert(UserProfile.builder().id(id).cefrLevel(CefrLevel.A1).build());
        mongoTemplate.insert(RefreshSession.builder().sessionId("auth-" + id)
                .accountId(id).expiresAt(Instant.now().plusSeconds(900)).build());
    }

    private Socket socket(String id, String ticket) throws Exception {
        Socket listener = new Socket(id);
        listener.socket = upgrade(id, "?ticket=" + ticket, ORIGIN, listener).get(8, TimeUnit.SECONDS);
        sockets.add(listener);
        return listener;
    }

    private CompletableFuture<WebSocket> upgrade(String id, String query, String origin, WebSocket.Listener listener) {
        return http.newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/speaking/" + id + query), listener);
    }

    private void assertHandshakeStatus(String id, String query, String origin, int status) {
        assertThatThrownBy(() -> upgrade(id, query, origin, new Socket(id)).get(8, TimeUnit.SECONDS))
                .hasCauseInstanceOf(WebSocketHandshakeException.class)
                .satisfies(error -> assertThat(((WebSocketHandshakeException) error.getCause()).getResponse().statusCode())
                        .isEqualTo(status));
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static byte[] frame(UUID id, int index, byte[] payload) {
        return ByteBuffer.allocate(28 + payload.length).putInt(0x53504b32).putInt(0x01000000)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).putInt(index).put(payload).array();
    }

    private static byte[] voice() {
        byte[] pcm = new byte[3200];
        Arrays.fill(pcm, (byte) 16);
        return pcm;
    }

    private final class Socket implements WebSocket.Listener {
        private final String id;
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final List<byte[]> audio = new CopyOnWriteArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private volatile WebSocket socket;

        private Socket(String id) { this.id = id; }

        String command(String type, UUID turn, Map<String, ?> payload) {
            String request = UUID.randomUUID().toString();
            var message = mapper.createObjectNode().put("type", type).put("sessionId", id).put("requestId", request);
            message.set("payload", mapper.valueToTree(payload));
            if (turn != null) message.put("turnId", turn.toString());
            socket.sendText(mapper.writeValueAsString(message), true).join();
            return request;
        }

        void binary(byte[] frame) { socket.sendBinary(ByteBuffer.wrap(frame), true).join(); }

        void closeNormally() throws Exception {
            if (!socket.isOutputClosed()) socket.sendClose(1000, "test ended").get(2, TimeUnit.SECONDS);
            closed.get(2, TimeUnit.SECONDS);
        }

        JsonNode next(String type) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            var seen = new java.util.ArrayList<String>();
            try {
                while (System.nanoTime() < deadline) {
                    var entry = messages.poll(100, TimeUnit.MILLISECONDS);
                    if (entry == null) continue;
                    if (type.equals(entry.path("type").asString())) return entry;
                    seen.add(entry.toString());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError("Missing " + type + "; received " + seen);
        }

        byte[] audioFor(UUID turn) {
            return audio.stream().filter(frame -> frame.length > 28 && ByteBuffer.wrap(frame).getLong(8) == turn.getMostSignificantBits()
                    && ByteBuffer.wrap(frame).getLong(16) == turn.getLeastSignificantBits()).findFirst()
                    .map(frame -> Arrays.copyOfRange(frame, 28, frame.length)).orElse(new byte[0]);
        }

        @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            closed.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(mapper.readTree(text.toString()));
                text.setLength(0);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer message, boolean last) {
            byte[] frame = new byte[message.remaining()];
            message.get(frame);
            audio.add(frame);
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
