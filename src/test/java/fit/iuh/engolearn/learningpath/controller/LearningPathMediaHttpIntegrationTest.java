package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.service.LearningPathDayPracticeService;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.model.MediaAsset;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfDockerAvailable
@Import(LearningPathMediaHttpIntegrationTest.StorageConfig.class)
class LearningPathMediaHttpIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String OWNER = "media-learner";
    private static final String MEDIA_ID = "b986d5cc-331b-465e-8da0-79140c89c08e";
    private static final String PASSAGE_ID = "media-passage";
    private static final String SKILL = "Listening for Gist";
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private DeliveryStorage storage;
    @Autowired private LearningPathDayPracticeService practice;

    @BeforeEach
    void resetDelivery() {
        storage.calls.set(0);
        storage.fail = false;
        SecurityContextHolder.clearContext();
    }

    @Test
    void creationPinsMediaWithoutDeliveryUrlsAndStartRenewalAndReviewKeepThatRecording() {
        seedAccount(OWNER, UserRole.USER);
        seedAsset(MEDIA_ID, "recording-original", "audio/mpeg");
        seedSource();
        var created = data(request("/api/learning-paths/by-assessment", HttpMethod.POST,
                "{\"assessmentAttemptId\":\"audio-source\",\"level\":\"B1\",\"microSkills\":[\"Listening for Gist\"]}", OWNER));
        String pathId = created.path("pathId").asString();
        var snapshots = mongoTemplate.getCollection("learning_path_questions").find().into(new ArrayList<>());
        assertThat(snapshots).hasSize(30);
        for (Document snapshot : snapshots) {
            var passage = snapshot.get("passage", Document.class);
            assertThat(passage.getString("audio_media_id")).isEqualTo(MEDIA_ID);
            assertThat(passage.get("audio")).isNull();
            assertThat(snapshot.toJson()).doesNotContain("delivery.test", "expiresAt", "legacy.test");
        }
        var storedPath = path(pathId);
        String questionId = storedPath.getList("weeks", Document.class).getFirst().getList("days", Document.class)
                .getFirst().getList("items", Document.class).getFirst().getString("question_id");
        String day = dayUrl(pathId);
        assertStatus(409, () -> request(audioUrl(pathId, questionId), HttpMethod.GET, null, OWNER));
        assertThat(path(pathId)).isEqualTo(storedPath);
        seedAsset("c083d5bc-6287-4807-8869-9226f19b638f", "recording-replacement", "audio/mpeg");
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", PASSAGE_ID),
                new Document("$set", new Document("audio_media_id", "c083d5bc-6287-4807-8869-9226f19b638f")
                        .append("status", "RETIRED").append("transcript", "Changed source transcript")));
        var startedResponse = request(day + "/start", HttpMethod.POST, null, OWNER);
        assertThat(startedResponse.getHeaders().getCacheControl()).isEqualTo("no-store");
        var started = data(startedResponse);
        assertThat(started.path("questions").size()).isEqualTo(10);
        for (JsonNode question : started.path("questions")) {
            assertAudio(question.path("passage").path("audio"));
            assertThat(question.has("correctOptionId")).isFalse();
            assertThat(question.has("explanation")).isFalse();
            assertThat(question.path("passage").has("transcript")).isFalse();
        }
        var savedPath = path(pathId);
        var assets = mongoTemplate.getCollection("media_assets").find().into(new ArrayList<>());
        var first = request(audioUrl(pathId, questionId), HttpMethod.GET, null, OWNER);
        var second = request(audioUrl(pathId, questionId), HttpMethod.GET, null, OWNER);
        assertAudio(data(first)); assertAudio(data(second));
        assertThat(data(first).path("url").asString()).isNotEqualTo(data(second).path("url").asString());
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(first.getBody()).doesNotContain("correctOptionId", "transcript", "explanation", "options");
        assertThat(path(pathId)).isEqualTo(savedPath);
        assertThat(mongoTemplate.getCollection("media_assets").find().into(new ArrayList<>())).isEqualTo(assets);
        assertThat(mongoTemplate.getCollection("learning_path_questions").find().into(new ArrayList<>())).isEqualTo(snapshots);
        request(day + "/submit", HttpMethod.POST, null, OWNER);
        var result = request(day + "/result", HttpMethod.GET, null, OWNER);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        var reviewedQuestion = data(result).path("questions").get(0);
        assertAudio(reviewedQuestion.path("passage").path("audio"));
        assertThat(reviewedQuestion.path("passage").path("transcript").asString()).isEqualTo("Original transcript");
        assertThat(reviewedQuestion.path("correctOptionId").asString()).isEqualTo("A");
        var closedPath = path(pathId);
        assertAudio(data(request(audioUrl(pathId, questionId), HttpMethod.GET, null, OWNER)));
        assertThat(path(pathId)).isEqualTo(closedPath);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void trustedOwnerCanRenewWithEveryInheritedRoleButAnotherUserCannot(UserRole role) {
        seedAccount(OWNER, role); seedAccount("other", UserRole.ADMIN);
        seedAsset(MEDIA_ID, "recording-original", "audio/mpeg");
        String pathId = seedDay("IN_PROGRESS", true);
        String url = audioUrl(pathId, "q-1");
        assertStatus(401, () -> request(url, HttpMethod.GET, null, null));
        assertStatus(404, () -> request(url + "?userId=" + OWNER, HttpMethod.GET, null, "other"));
        assertThat(storage.calls).hasValue(0);
        assertAudio(data(request(url, HttpMethod.GET, null, OWNER)));
        int beforeDenial = storage.calls.get();
        assertStatus(404, () -> request(audioUrl(pathId, "q-other-day"), HttpMethod.GET, null, OWNER));
        assertStatus(404, () -> request(url.replace("/days/1/", "/days/2/"), HttpMethod.GET, null, OWNER));
        assertStatus(404, () -> request(url.replace(pathId, new ObjectId().toHexString()), HttpMethod.GET, null, OWNER));
        assertStatus(400, () -> request(url.replace("/weeks/1/", "/weeks/4/"), HttpMethod.GET, null, OWNER));
        assertThat(storage.calls).hasValue(beforeDenial);
        mongoTemplate.getCollection("users").updateOne(new Document("_id", OWNER), new Document("$set", new Document("locked", true)));
        assertStatus(401, () -> request(url, HttpMethod.GET, null, OWNER));
        assertThat(storage.calls).hasValue(beforeDenial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unavailable", "wrong-kind"})
    void creationFailsBeforeWritingSnapshotsOrPathWhenManagedAudioCannotBeDelivered(String failure) {
        seedAccount(OWNER, UserRole.USER);
        if (!failure.equals("missing")) seedAsset(MEDIA_ID, "recording-original", failure.equals("wrong-kind") ? "image/png" : "audio/mpeg");
        seedSource();
        storage.fail = failure.equals("unavailable");
        var source = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertStatus(failure.equals("missing") ? 404 : 503, () -> request("/api/learning-paths/by-assessment", HttpMethod.POST,
                "{\"assessmentAttemptId\":\"audio-source\",\"level\":\"B1\",\"microSkills\":[\"Listening for Gist\"]}", OWNER));
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(source);
    }

    @Test
    void renewalHasAMethodBoundaryAndUsesTheTrustedSessionIdentity() {
        assertThatThrownBy(() -> practice.getAudio(new ObjectId().toHexString(), 1, 1, "q-1"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(storage.calls).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"day-deadline", "week-deadline", "next-run", "inactivity", "cancelled", "not-started", "locked-week"})
    void practiceAccessIsCheckedBeforeResolvingOrWritingAnything(String reason) {
        seedAccount(OWNER, UserRole.USER); seedAsset(MEDIA_ID, "recording-original", "audio/mpeg");
        String pathId = seedDay("IN_PROGRESS", true);
        Document change = switch (reason) {
            case "day-deadline" -> new Document("weeks.0.days.0.auto_close_at", Date.from(Instant.now().minusSeconds(1)));
            case "week-deadline" -> new Document("weeks.0.close_at", Date.from(Instant.now().minusSeconds(1)));
            case "next-run" -> new Document("next_run_at", Date.from(Instant.now().minusSeconds(1)));
            case "inactivity" -> new Document("inactivity.cancel_at", Date.from(Instant.now().minusSeconds(1)));
            case "cancelled" -> new Document("status", "CANCELLED").append("is_active", false);
            case "not-started" -> new Document("weeks.0.days.0.status", "NOT_STARTED");
            default -> new Document("weeks.0.status", "LOCKED");
        };
        mongoTemplate.getCollection("learning_paths").updateOne(new Document("_id", new ObjectId(pathId)), new Document("$set", change));
        Document before = path(pathId);
        assertStatus(409, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        assertThat(storage.calls).hasValue(0);
        assertThat(path(pathId)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "SUBMITTED", "CLOSED"})
    void endedDaysCanRenewForReviewAfterTheirPracticeDeadlines(String state) {
        seedAccount(OWNER, UserRole.USER); seedAsset(MEDIA_ID, "recording-original", "audio/mpeg");
        String pathId = seedDay(state, true);
        mongoTemplate.getCollection("learning_paths").updateOne(new Document("_id", new ObjectId(pathId)),
                new Document("$set", new Document("status", "COMPLETED").append("is_active", false)
                        .append("next_run_at", null).append("weeks.0.status", "CLOSED")
                        .append("weeks.0.close_at", Date.from(Instant.now().minusSeconds(60)))
                        .append("weeks.0.days.0.auto_close_at", Date.from(Instant.now().minusSeconds(60)))));
        Document before = path(pathId);
        assertAudio(data(request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER)));
        var review = data(request(dayUrl(pathId) + "/result", HttpMethod.GET, null, OWNER));
        assertAudio(review.path("questions").get(0).path("passage").path("audio"));
        assertThat(path(pathId)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unavailable", "wrong-kind"})
    void managedAudioNeverFallsBackToLegacyAndFailedStartDoesNotStartTheDay(String failure) {
        seedAccount(OWNER, UserRole.USER);
        if (!failure.equals("missing")) seedAsset(MEDIA_ID, "recording-original", failure.equals("wrong-kind") ? "image/png" : "audio/mpeg");
        storage.fail = failure.equals("unavailable");
        String pathId = seedDay("NOT_STARTED", true);
        Document before = path(pathId);
        int status = failure.equals("missing") ? 404 : 503;
        assertStatus(status, () -> request(dayUrl(pathId) + "/start", HttpMethod.POST, null, OWNER));
        assertThat(path(pathId)).isEqualTo(before);
        mongoTemplate.getCollection("learning_paths").updateOne(new Document("_id", new ObjectId(pathId)),
                new Document("$set", new Document("weeks.0.days.0.status", "IN_PROGRESS")));
        assertStatus(status, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        mongoTemplate.getCollection("learning_paths").updateOne(new Document("_id", new ObjectId(pathId)),
                new Document("$set", new Document("weeks.0.days.0.status", "SUBMITTED")
                        .append("weeks.0.days.0.progress", new Document("assigned", 10).append("answered", 0).append("correct", 0))
                        .append("weeks.0.days.0.result", new Document("wrong", 0).append("completion_rate", 0.0))));
        assertStatus(status, () -> request(dayUrl(pathId) + "/result", HttpMethod.GET, null, OWNER));
    }

    @Test
    void legacySnapshotsRetainTheirRecordingAndNoAudioIsExplicitlyMissing() {
        seedAccount(OWNER, UserRole.USER);
        String pathId = seedDay("IN_PROGRESS", false);
        var legacy = data(request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        assertThat(legacy.path("url").asString()).isEqualTo("https://legacy.test/original.mp3");
        assertThat(legacy.path("expiresAt").isNull()).isTrue();
        assertThat(legacy.path("durationSec").asInt()).isEqualTo(12);
        assertThat(storage.calls).hasValue(0);
        mongoTemplate.getCollection("learning_path_questions").updateOne(new Document("question_id", "q-1"),
                new Document("$unset", new Document("passage.audio", "")));
        assertStatus(404, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
    }

    @Test
    void aMalformedReferenceAndDuplicateOrMissingSnapshotFailWithoutPoolLookup() {
        seedAccount(OWNER, UserRole.USER); seedAsset(MEDIA_ID, "recording-original", "audio/mpeg");
        String pathId = seedDay("IN_PROGRESS", true);
        var query = new Document("path_id", new ObjectId(pathId)).append("question_id", "q-1");
        mongoTemplate.getCollection("learning_path_questions").updateOne(query,
                new Document("$set", new Document("passage.audio_media_id", List.of(MEDIA_ID))));
        assertStatus(500, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        mongoTemplate.getCollection("learning_path_questions").updateOne(query,
                new Document("$set", new Document("passage.audio_media_id", MEDIA_ID)));
        Document duplicate = mongoTemplate.getCollection("learning_path_questions").find(query).first();
        duplicate.remove("_id");
        mongoTemplate.getCollection("learning_path_questions").insertOne(duplicate);
        assertStatus(500, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        mongoTemplate.getCollection("learning_path_questions").deleteMany(query);
        assertStatus(500, () -> request(audioUrl(pathId, "q-1"), HttpMethod.GET, null, OWNER));
        assertThat(storage.calls).hasValue(0);
    }

    private void assertAudio(JsonNode audio) {
        assertThat(audio.path("url").asString()).startsWith("https://delivery.test/recording-original?");
        assertThat(Instant.parse(audio.path("expiresAt").asString())).isAfter(Instant.now());
        assertThat(audio.path("contentType").asString()).isEqualTo("audio/mpeg");
        assertThat(audio.path("format").asString()).isEqualTo("mp3");
        assertThat(audio.path("durationSec").isNull()).isTrue();
    }

    private JsonNode data(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody()).path("data");
    }

    private void assertStatus(int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(RestClientResponseException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }

    private ResponseEntity<String> request(String path, HttpMethod method, String body, String user) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (user != null) {
            String session = UUID.randomUUID().toString();
            mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", session).append("account_id", user)
                    .append("expires_at", Date.from(Instant.now().plusSeconds(3600))).append("revoked", false));
            headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + tokens.create(user, session, "access", 900));
        }
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String dayUrl(String pathId) { return "/api/learning-paths/" + pathId + "/weeks/1/days/1"; }
    private String audioUrl(String pathId, String questionId) { return dayUrl(pathId) + "/questions/" + questionId + "/audio"; }
    private Document path(String id) { return mongoTemplate.getCollection("learning_paths").find(new Document("_id", new ObjectId(id))).first(); }

    private void seedAccount(String user, UserRole role) {
        mongoTemplate.getCollection("users").insertOne(new Document("_id", user).append("email", user + "@example.test")
                .append("password_hash", "unused").append("role", role.name()).append("locked", false));
        mongoTemplate.getCollection("user_profiles").insertOne(new Document("_id", user).append("cefr_level", "B1"));
    }

    private void seedAsset(String id, String recording, String contentType) {
        mongoTemplate.insert(MediaAsset.builder().id(id).purpose("LEARNING_PATH_PASSAGE_AUDIO")
                .accessPolicy(MediaAccessPolicy.PROTECTED).contentType(contentType).sizeBytes(100)
                .storage(MediaStorageMetadataVo.builder().provider(MediaProvider.CLOUDINARY).publicId(recording)
                        .resourceType(MediaResourceType.VIDEO).deliveryType(MediaDeliveryType.AUTHENTICATED).format("mp3").build()).build());
    }

    private Document passage(boolean managed) {
        var passage = new Document("passage_id", PASSAGE_ID).append("title", "Original recording")
                .append("transcript", "Original transcript").append("revision", 1)
                .append("audio", new Document("url", "https://legacy.test/original.mp3").append("duration_sec", 12).append("format", "mp3"));
        if (managed) passage.append("audio_media_id", MEDIA_ID);
        return passage;
    }

    private List<Document> options() {
        return List.of("A", "B", "C", "D").stream().map(id -> new Document("option_id", id).append("text", "Option " + id)).toList();
    }

    private String seedDay(String status, boolean managed) {
        ObjectId id = new ObjectId(); Date deadline = Date.from(Instant.now().plusSeconds(3600));
        var items = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("order", i).append("question_id", "q-" + i).append("micro_skill", SKILL)).toList();
        var day = new Document("day_no", 1).append("status", status).append("items", items)
                .append("started_at", Date.from(Instant.now())).append("auto_close_at", deadline);
        if (List.of("COMPLETED", "SUBMITTED", "CLOSED").contains(status)) {
            day.append("closed_at", Date.from(Instant.now()))
                    .append("progress", new Document("assigned", 10).append("answered", 0).append("correct", 0))
                    .append("result", new Document("wrong", 0).append("completion_rate", 0.0));
        }
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", id).append("user_id", OWNER)
                .append("status", "IN_PROGRESS").append("is_active", true).append("config", new Document("questions_per_day", 10))
                .append("next_run_at", deadline).append("inactivity", new Document("cancel_at", deadline))
                .append("weeks", List.of(new Document("week_no", 1).append("status", "OPEN").append("close_at", deadline).append("days", List.of(day)))));
        mongoTemplate.getCollection("learning_path_questions").insertMany(IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("path_id", id)
                .append("user_id", OWNER).append("question_id", "q-" + i).append("micro_skill", SKILL).append("skill", "LISTENING")
                .append("prompt", "Question " + i).append("passage", passage(managed)).append("options", options())
                .append("correct_option_id", "A").append("explanation", "Original explanation")).toList());
        return id.toHexString();
    }

    private void seedSource() {
        var skills = List.of("R02", "R01", "R04", "L01", "L03").stream().map(code -> new Document("code", code)
                .append("section", code.startsWith("L") ? "LISTENING" : "READING").append("correct", code.equals("L01") ? 2 : 5)
                .append("total", 10).append("percentage", code.equals("L01") ? 20 : 50)
                .append("band", code.equals("L01") ? "NEEDS_PRACTICE" : "MONITOR")).toList();
        mongoTemplate.getCollection("assessment_attempts").insertOne(new Document("_id", "audio-source").append("user_id", OWNER)
                .append("status", "READY").append("version", 0L).append("level", "B1").append("submitted_at", Date.from(Instant.now()))
                .append("paper", new Document("paper_id", "audio-paper").append("series", "AUDIO").append("version", 1).append("demonstration", false))
                .append("correct_count", 22).append("reading_correct", 15).append("listening_correct", 7).append("skill_results", skills));
        mongoTemplate.getCollection("question_pool_passages").insertOne(passage(true).append("skill", "LISTENING").append("level", "B1").append("status", "ACTIVE"));
        mongoTemplate.getCollection("question_pool").insertMany(IntStream.rangeClosed(1, 30).mapToObj(i -> new Document("question_id", "pool-media-" + i)
                .append("micro_skill", SKILL).append("skill", "LISTENING").append("level", "B1").append("status", "ACTIVE")
                .append("passage_id", PASSAGE_ID).append("prompt", "Question " + i).append("options", options())
                .append("correct_option_id", "A").append("explanation", "Original explanation").append("revision", 1)).toList());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfig {
        @Bean @Primary DeliveryStorage deliveryStorage() { return new DeliveryStorage(); }
    }

    // Only the external storage port is substituted; MediaManagement, MongoDB, auth and HTTP are real.
    static class DeliveryStorage implements MediaStorageProvider {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean fail;
        @Override public MediaStorageMetadataVo upload(String id, MediaFileDescriptor descriptor, MediaAccessPolicy policy, InputStream content) { throw new UnsupportedOperationException("No external uploads in learner tests"); }
        @Override public URI publicUrl(MediaStorageMetadataVo asset) { throw new UnsupportedOperationException("Only protected audio in this suite"); }
        @Override public URI privateDownloadUrl(MediaStorageMetadataVo asset, Instant expiresAt) {
            int sequence = calls.incrementAndGet();
            if (fail) throw new MediaStorageUnavailableException("Storage unavailable");
            return URI.create("https://delivery.test/" + asset.getPublicId() + "?expires=" + expiresAt.getEpochSecond() + "&request=" + sequence);
        }
        @Override public void destroy(MediaStorageMetadataVo asset) { throw new UnsupportedOperationException("No media deletion in learner tests"); }
    }
}
