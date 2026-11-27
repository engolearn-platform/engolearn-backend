package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.exception.PassageRetireConflictException;
import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageReadinessService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionReadinessService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
class QuestionPoolQuestionPublicationIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/learning-paths/questions";
    private static final List<QuestionReviewCheck> READING_CHECKS = Arrays.stream(QuestionReviewCheck.values())
            .filter(value -> value != QuestionReviewCheck.AUDIO_MATCHES_TRANSCRIPT && value != QuestionReviewCheck.AUDIO_QUALITY_REVIEWED).toList();
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private QuestionPoolQuestionReadinessService readiness;
    @Autowired private QuestionPoolQuestionManagementService service;
    @Autowired private QuestionPoolPassageReadinessService passageReadiness;
    @Autowired private QuestionPoolPassageManagementService passageService;
    @MockitoSpyBean private QuestionPoolQuestionRepository questions;
    @MockitoSpyBean private QuestionPoolPassageRepository passages;
    @MockitoSpyBean private QuestionPoolRepository references;
    @MockitoBean private MediaManagement media;

    @Test
    void readinessIsReadOnlyAndDoesNotClaimEditorialApprovalOrRequireThirtyQuestions() {
        seedQuestion(false, null);
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("sentinel", "preserve"));
        mongoTemplate.getCollection("learning_path_questions").insertOne(new Document("question_id", "q").append("prompt", "Pinned"));
        var before = snapshot();
        var response = request(HttpMethod.GET, "/q/readiness", null, UserRole.CONTENT_MANAGER);
        var data = data(response, 200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(data.path("structurallyValid").asBoolean()).isTrue();
        assertThat(data.path("revision").asInt()).isEqualTo(3);
        assertThat(data.path("blockers").size()).isZero();
        assertThat(data.path("warnings").size()).isZero();
        assertThat(data.path("requiredReviewChecks").size()).isEqualTo(7);
        assertThat(data.has("canPublish")).isFalse();
        for (String field : List.of("prompt", "options", "correctOptionId", "explanation", "audio")) assertThat(data.has(field)).isFalse();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void publishesReadingWithRevisionBoundReviewTrustedAuditAndUnchangedSnapshots(UserRole role) {
        seedQuestion(false, null);
        mongoTemplate.getCollection("learning_path_questions").insertOne(new Document("question_id", "q").append("prompt", "Pinned"));
        var source = question();
        var snapshot = mongoTemplate.getCollection("learning_path_questions").find().first();
        var response = request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), role);
        var data = data(response, 200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(data.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(data.path("revision").asInt()).isEqualTo(4);
        assertThat(data.path("reviewedRevision").asInt()).isEqualTo(3);
        assertThat(data.path("publishedBy").asString()).isEqualTo("question-publisher");
        assertThat(data.path("confirmedReviewChecks").size()).isEqualTo(7);
        var saved = question();
        assertThat(saved.getString("status")).isEqualTo("ACTIVE");
        assertThat(saved.getInteger("revision")).isEqualTo(4);
        assertThat(saved.getString("created_by")).isEqualTo("author");
        assertThat(saved.getDate("created_at")).isEqualTo(source.getDate("created_at"));
        for (String field : List.of("prompt", "options", "correct_option_id", "explanation", "import_marker")) assertThat(saved.get(field)).isEqualTo(source.get(field));
        var review = saved.get("publication_review", Document.class);
        assertThat(review.getInteger("source_revision")).isEqualTo(3);
        assertThat(review.getString("reviewed_by")).isEqualTo("question-publisher");
        assertThat(review.getDate("reviewed_at")).isEqualTo(saved.getDate("updated_at"));
        assertThat(review.getList("confirmed_checks", String.class)).containsExactlyElementsOf(READING_CHECKS.stream().map(Enum::name).toList());
        var detail = data(request(HttpMethod.GET, "/q", null, role), 200);
        assertThat(detail.path("publicationReview").path("sourceRevision").asInt()).isEqualTo(3);
        assertThat(mongoTemplate.getCollection("learning_path_questions").find().first()).isEqualTo(snapshot);
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), role), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(question()).isEqualTo(saved);
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":4,\"prompt\":\"Changed\"}", role), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request(HttpMethod.DELETE, "/q?revision=4", null, role), 409, "QUESTION_WRITE_CONFLICT");
        verifyNoInteractions(media);
    }

    static Stream<Arguments> invalidContent() {
        return Stream.of(
                Arguments.of(new Document("skill", "SPEAKING"), "INVALID_SKILL"),
                Arguments.of(new Document("skill", List.of("READING")), "INVALID_STORED_FIELD"),
                Arguments.of(new Document("micro_skill", "Listening for Gist"), "INVALID_MICRO_SKILL"),
                Arguments.of(new Document("level", "INVALID"), "INVALID_LEVEL"),
                Arguments.of(new Document("revision", "three"), "INVALID_REVISION"),
                Arguments.of(new Document("revision", 0), "INVALID_REVISION"),
                Arguments.of(new Document("revision", Integer.MAX_VALUE), "REVISION_EXHAUSTED"),
                Arguments.of(new Document("status", "UNKNOWN"), "QUESTION_NOT_DRAFT"),
                Arguments.of(new Document("prompt", " "), "PROMPT_REQUIRED"),
                Arguments.of(new Document("explanation", null), "EXPLANATION_REQUIRED"),
                Arguments.of(new Document("prompt", "x".repeat(50001)), "CONTENT_TOO_LONG"),
                Arguments.of(new Document("correct_option_id", "E"), "CORRECT_OPTION_INVALID"),
                Arguments.of(new Document("options", new Document("bad", true)), "INVALID_STORED_FIELD"),
                Arguments.of(new Document("options", List.of(new Document("option_id", "A").append("text", "Only one"))), "OPTIONS_COUNT_INVALID"),
                Arguments.of(new Document("options", Arrays.asList(null, new Document("option_id", "B").append("text", 42))), "INVALID_STORED_FIELD"));
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    void readinessDiagnosesMalformedImportedContentWithoutEnumMappingOrWrites(Document changes, String code) {
        seedQuestion(false, null);
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"), new Document("$set", changes));
        var before = question();
        var data = readReady();
        assertThat(data.path("structurallyValid").asBoolean()).isFalse();
        assertThat(codes(data, "blockers")).contains(code);
        assertThat(question()).isEqualTo(before);
    }

    @Test
    void duplicateChoiceTextAndIdsBlockPublicationEvenWithAllConfirmations() {
        seedQuestion(false, null);
        var options = question().getList("options", Document.class);
        options.get(1).put("text", "  OPTION   A  ");
        options.get(3).put("option_id", "A");
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"), new Document("$set", new Document("options", options)));
        assertThat(codes(readReady(), "blockers")).contains("OPTION_TEXTS_DUPLICATE", "OPTION_IDS_INVALID");
        var response = request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN);
        assertError(response, 422, "QUESTION_NOT_READY");
        assertThat(json.readTree(response.getBody()).path("readiness").path("structurallyValid").asBoolean()).isFalse();
        assertThat(question().get("publication_review")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "RETIRED"})
    void referencedPassageMustBeActive(String status) {
        seedQuestion(false, "p");
        seedPassage(false, status);
        assertThat(codes(readReady(), "blockers")).contains("PASSAGE_NOT_ACTIVE");
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 422, "QUESTION_NOT_READY");
    }

    @Test
    void publicationChecksPassageExistenceClassificationAndCurrentContent() {
        seedQuestion(false, "p");
        assertThat(codes(readReady(), "blockers")).contains("PASSAGE_NOT_FOUND");
        seedPassage(false, "ACTIVE");
        setPassage(new Document("level", "A2"));
        assertThat(codes(readReady(), "blockers")).contains("PASSAGE_CLASSIFICATION_MISMATCH");
        setPassage(new Document("level", "B1").append("text", " "));
        assertThat(codes(readReady(), "blockers")).contains("READING_TEXT_REQUIRED");
        setPassage(new Document("text", "Text").append("revision", Integer.MAX_VALUE));
        assertThat(codes(readReady(), "blockers")).contains("PASSAGE_REVISION_EXHAUSTED");
    }

    @Test
    void legacyListeningRequiresTranscriptAndValidAudioAndReturnsWarningWhenUsable() {
        seedQuestion(true, null);
        assertThat(codes(readReady(), "blockers")).contains("PASSAGE_REQUIRED");
        setQuestion(new Document("passage_id", "p"));
        seedPassage(true, "ACTIVE");
        setPassage(new Document("transcript", " ").append("audio", null));
        assertThat(codes(readReady(), "blockers")).contains("TRANSCRIPT_REQUIRED", "AUDIO_MISSING");
        setPassage(new Document("transcript", "Transcript").append("audio", new Document("url", "javascript:bad")));
        assertThat(codes(readReady(), "blockers")).contains("AUDIO_URL_INVALID");
        setPassage(new Document("audio", new Document("url", "/learning-path-audio/test.mp3")));
        var ready = readReady();
        assertThat(ready.path("structurallyValid").asBoolean()).isTrue();
        assertThat(ready.path("requiredReviewChecks").size()).isEqualTo(9);
        assertThat(codes(ready, "warnings")).containsExactly("LEGACY_AUDIO_PLAYBACK_UNVERIFIED");
        verifyNoInteractions(media);
    }

    @Test
    void publishesLegacyListeningAndRecordsReviewedPassageRevisionBeforeFenceIncrement() {
        seedQuestion(true, "p");
        seedPassage(true, "ACTIVE");
        var sourcePassage = passage();
        var data = data(request(HttpMethod.POST, "/q/publish", publication(3, List.of(QuestionReviewCheck.values())), UserRole.ADMIN), 200);
        assertThat(data.path("reviewedPassageRevision").asInt()).isEqualTo(1);
        assertThat(codes(data, "warnings")).containsExactly("LEGACY_AUDIO_PLAYBACK_UNVERIFIED");
        assertThat(passage().getInteger("revision")).isEqualTo(2);
        assertThat(passage().getString("status")).isEqualTo("ACTIVE");
        assertThat(passage().get("audio")).isEqualTo(sourcePassage.get("audio"));
        assertThat(passage().getString("transcript")).isEqualTo(sourcePassage.getString("transcript"));
        assertThat(question().get("publication_review", Document.class).getInteger("passage_revision")).isEqualTo(1);
        assertThat(question().get("publication_review", Document.class).getList("confirmed_checks", String.class)).hasSize(9);
        verifyNoInteractions(media);
    }

    @Test
    void mediaBackedListeningIsReadyButMissingOrUnavailableMediaNeverFallsBackToLegacyAudio() {
        seedQuestion(true, "p");
        seedPassage(true, "ACTIVE");
        setPassage(new Document("audio_media_id", "media-id"));
        when(media.resolve("media-id")).thenReturn(new MediaDeliveryDto(URI.create("https://example.test/private-signed.mp3"), Instant.now().plusSeconds(60), "audio/mpeg"));
        var before = snapshot();
        var ready = readReady();
        assertThat(codes(ready, "blockers")).isEmpty();
        assertThat(ready.path("structurallyValid").asBoolean()).isTrue();
        assertThat(ready.toString()).doesNotContain("private-signed.mp3");
        doThrow(new MediaNotFoundException("media-id")).when(media).resolve("media-id");
        assertThat(codes(readReady(), "blockers")).contains("AUDIO_MEDIA_NOT_FOUND");
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, List.of(QuestionReviewCheck.values())), UserRole.ADMIN), 422, "QUESTION_NOT_READY");
        doThrow(new MediaStorageUnavailableException("provider secret")).when(media).resolve("media-id");
        assertError(request(HttpMethod.GET, "/q/readiness", null, UserRole.ADMIN), 503, "MEDIA_STORAGE_UNAVAILABLE");
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, List.of(QuestionReviewCheck.values())), UserRole.ADMIN), 503, "MEDIA_STORAGE_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void reviewedManagedListeningPublishesWithoutCopyingItsDeliveryUrl() {
        seedQuestion(true, "p");
        seedPassage(true, "ACTIVE");
        setPassage(new Document("audio_media_id", "media-id"));
        when(media.resolve("media-id")).thenReturn(new MediaDeliveryDto(URI.create("https://example.test/private-signed.mp3"), Instant.now().plusSeconds(60), "audio/mpeg"));
        var response = request(HttpMethod.POST, "/q/publish", publication(3, List.of(QuestionReviewCheck.values())), UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(question().getString("status")).isEqualTo("ACTIVE");
        assertThat(passage().getString("audio_media_id")).isEqualTo("media-id");
        assertThat(question().toJson() + passage().toJson()).doesNotContain("private-signed.mp3");
        assertThat(question().get("publication_review", Document.class).getList("confirmed_checks", String.class)).hasSize(9);
    }

    @Test
    void mediaDeliveryValidityUsesTheSamePassageChecks() {
        seedQuestion(true, "p");
        seedPassage(true, "ACTIVE");
        setPassage(new Document("audio_media_id", "media-id"));
        when(media.resolve("media-id")).thenReturn(new MediaDeliveryDto(URI.create("/relative.mp3"), Instant.now().minusSeconds(5), "image/png"));
        assertThat(codes(readReady(), "blockers")).contains("AUDIO_DELIVERY_URL_INVALID", "AUDIO_CONTENT_TYPE_INVALID", "AUDIO_DELIVERY_EXPIRED");
    }

    @Test
    void missingPartialOrUnrelatedReviewIsRejectedWithoutPartialApproval() {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var before = snapshot();
        for (List<QuestionReviewCheck> checks : List.of(List.<QuestionReviewCheck>of(), List.of(QuestionReviewCheck.LEVEL_APPROPRIATE), List.of(QuestionReviewCheck.values()))) {
            var response = request(HttpMethod.POST, "/q/publish", publication(3, checks), UserRole.ADMIN);
            assertError(response, 422, "QUESTION_REVIEW_REQUIRED");
            assertThat(json.readTree(response.getBody()).path("readiness").path("requiredReviewChecks").size()).isEqualTo(7);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void freshEvaluationRejectsChangedContentDespiteEarlierSuccessfulReadiness() {
        seedQuestion(false, null);
        assertThat(readReady().path("structurallyValid").asBoolean()).isTrue();
        var options = question().getList("options", Document.class);
        options.get(1).put("text", "Option A");
        setQuestion(new Document("options", options).append("revision", 4));
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request(HttpMethod.POST, "/q/publish", publication(4, READING_CHECKS), UserRole.ADMIN), 422, "QUESTION_NOT_READY");
        assertThat(question().getString("status")).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RETIRED"})
    void nonDraftPublicationFailsBeforeResolvingMedia(String status) {
        seedQuestion(true, "p");
        seedPassage(true, "ACTIVE");
        setPassage(new Document("audio_media_id", "media-id"));
        setQuestion(new Document("status", status));
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, List.of(QuestionReviewCheck.values())), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"revision\":0,\"confirmedReviewChecks\":[]}",
            "{\"revision\":3}", "{\"revision\":3,\"confirmedReviewChecks\":[null]}",
            "{\"revision\":3,\"confirmedReviewChecks\":[\"UNKNOWN\"]}"})
    void malformedPublicationReturns400(String body) {
        assertError(request(HttpMethod.POST, "/q/publish", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
    }

    @Test
    void endpointsValidateIdsMissingQuestionsRevisionExhaustionAndJsonContentType() {
        assertError(request(HttpMethod.GET, "/missing/readiness", null, UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        assertError(request(HttpMethod.POST, "/missing/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        for (String id : List.of(" ", "x".repeat(101))) {
            assertError(request(HttpMethod.GET, "/" + id + "/readiness", null, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
            assertError(request(HttpMethod.POST, "/" + id + "/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        seedQuestion(false, null);
        setQuestion(new Document("revision", Integer.MAX_VALUE));
        assertError(request(HttpMethod.POST, "/q/publish", publication(Integer.MAX_VALUE, READING_CHECKS), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request(HttpMethod.POST, "/q/publish", "{}", UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void httpAndServiceProxyAuthorizeBothOperationsAndActivePassageValidation() {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var publish = new PublishQuestionPoolQuestionRequest(3, Set.copyOf(READING_CHECKS));
        for (UserRole role : new UserRole[]{null, UserRole.USER}) {
            int status = role == null ? 401 : 403;
            String code = role == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
            assertError(request(HttpMethod.GET, "/q/readiness", null, role), status, code);
            assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), role), status, code);
        }
        assertThat(AopUtils.isAopProxy(readiness)).isTrue();
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> readiness.check("q")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.publish("q", publish)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> passageReadiness.checkForQuestionPublication("p")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> readiness.check("q")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.publish("q", publish)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> passageReadiness.checkForQuestionPublication("p")).isInstanceOf(AccessDeniedException.class);
            for (UserRole role : List.of(UserRole.CONTENT_MANAGER, UserRole.ADMIN)) {
                authenticate(role);
                assertThat(readiness.check("q").structurallyValid()).isTrue();
                assertThat(passageReadiness.checkForQuestionPublication("p").structurallyValid()).isTrue();
            }
            assertThat(service.publish("q", publish).revision()).isEqualTo(4);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void lostQuestionPublicationCasRollsBackPassageFenceAndReview() {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var before = snapshot();
        doReturn(Optional.empty()).when(questions).publishDraft(eq("q"), eq(3), any());
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void databaseFailuresReturn503AndRollBackBothWrites() {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var before = snapshot();
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).publishDraft(eq("q"), eq(3), any());
        assertError(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).findReadinessSource("q");
        assertError(request(HttpMethod.GET, "/q/readiness", null, UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
    }

    @Test
    void concurrentPublicationsHaveOneWinnerAndPreserveOneReview() throws Exception {
        seedQuestion(false, null);
        headers(UserRole.ADMIN);
        var barrier = new CyclicBarrier(2);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (reads.incrementAndGet() <= 2) barrier.await(15, TimeUnit.SECONDS);
            return result;
        }).when(questions).findReadinessSource("q");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN).getStatusCode().value());
            var second = executor.submit(() -> request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN).getStatusCode().value());
            assertThat(List.of(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(question().getInteger("revision")).isEqualTo(4);
        assertThat(question().get("publication_review", Document.class).getInteger("source_revision")).isEqualTo(3);
    }

    @Test
    void passageRetirementWinningRacePreventsQuestionPublication() throws Exception {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            reached.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Fence wait timed out");
            return invocation.callRealMethod();
        }).when(passages).fenceQuestionPublication(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var publishing = executor.submit(() -> request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN));
            try {
                assertThat(reached.await(15, TimeUnit.SECONDS)).isTrue();
                authenticate(UserRole.ADMIN);
                passageService.retire("p", new fit.iuh.engolearn.learningpath.dto.request.RetireQuestionPoolPassageRequest(1));
            } finally { SecurityContextHolder.clearContext(); release.countDown(); }
            assertError(publishing.get(25, TimeUnit.SECONDS), 409, "QUESTION_WRITE_CONFLICT");
        }
        assertThat(question().getString("status")).isEqualTo("DRAFT");
        assertThat(question().get("publication_review")).isNull();
        assertThat(passage().getString("status")).isEqualTo("RETIRED");
    }

    @Test
    void publicationWinningAfterRetirementReferenceCountMakesRetirementCasFail() throws Exception {
        seedQuestion(false, "p");
        seedPassage(false, "ACTIVE");
        var counted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            var count = invocation.callRealMethod();
            counted.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Reference wait timed out");
            return count;
        }).when(references).countActivePassageReferences("p");
        try (var executor = Executors.newSingleThreadExecutor()) {
            var retiring = executor.submit(() -> {
                try {
                    authenticate(UserRole.ADMIN);
                    return catchThrowable(() -> passageService.retire("p", new fit.iuh.engolearn.learningpath.dto.request.RetireQuestionPoolPassageRequest(1)));
                } finally { SecurityContextHolder.clearContext(); }
            });
            try {
                assertThat(counted.await(15, TimeUnit.SECONDS)).isTrue();
                data(request(HttpMethod.POST, "/q/publish", publication(3, READING_CHECKS), UserRole.ADMIN), 200);
            } finally { release.countDown(); }
            assertThat(retiring.get(25, TimeUnit.SECONDS)).isInstanceOf(PassageRetireConflictException.class);
        }
        assertThat(question().getString("status")).isEqualTo("ACTIVE");
        assertThat(passage().getString("status")).isEqualTo("ACTIVE");
        assertThat(passage().getInteger("revision")).isEqualTo(2);
    }

    private void seedQuestion(boolean listening, String passageId) {
        mongoTemplate.getCollection("question_pool").insertOne(new Document("question_id", "q").append("revision", 3).append("status", "DRAFT")
                .append("skill", listening ? "LISTENING" : "READING").append("micro_skill", listening ? "Listening for Gist" : "Reading for Gist")
                .append("level", "B1").append("passage_id", passageId).append("prompt", "Prompt")
                .append("options", List.of("A", "B", "C", "D").stream().map(id -> new Document("option_id", id).append("text", "Option " + id)).toList())
                .append("correct_option_id", "B").append("explanation", "Evidence").append("created_by", "author")
                .append("created_at", Date.from(Instant.parse("2026-11-26T01:00:00Z"))).append("updated_at", Date.from(Instant.parse("2026-11-26T01:00:00Z")))
                .append("import_marker", "preserve"));
    }

    private void seedPassage(boolean listening, String status) {
        var document = new Document("passage_id", "p").append("revision", 1).append("status", status)
                .append("skill", listening ? "LISTENING" : "READING").append("level", "B1").append("title", "Title");
        if (listening) document.append("transcript", "Transcript").append("audio", new Document("url", "/learning-path-audio/test.mp3"));
        else document.append("text", "Reading text");
        mongoTemplate.getCollection("question_pool_passages").insertOne(document);
    }

    private void setQuestion(Document changes) { mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"), new Document("$set", changes)); }
    private void setPassage(Document changes) { mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", "p"), new Document("$set", changes)); }
    private Document question() { return mongoTemplate.getCollection("question_pool").find(new Document("question_id", "q")).first(); }
    private Document passage() { return mongoTemplate.getCollection("question_pool_passages").find(new Document("passage_id", "p")).first(); }
    private JsonNode readReady() { return data(request(HttpMethod.GET, "/q/readiness", null, UserRole.ADMIN), 200); }
    private List<List<Document>> snapshot() {
        return List.of("question_pool", "question_pool_passages", "learning_paths", "learning_path_questions", "media_assets").stream()
                .map(name -> (List<Document>) mongoTemplate.getCollection(name).find().into(new ArrayList<Document>())).toList();
    }

    private String publication(int revision, List<QuestionReviewCheck> checks) {
        return json.writeValueAsString(new PublishQuestionPoolQuestionRequest(revision, new HashSet<>(checks)));
    }

    private ResponseEntity<String> request(HttpMethod method, String suffix, String body, UserRole role) {
        return request(method, suffix, body, role, MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<String> request(HttpMethod method, String suffix, String body, UserRole role, MediaType contentType) {
        var headers = headers(role);
        headers.setContentType(contentType);
        int separator = suffix.indexOf('?');
        var uri = org.springframework.web.util.UriComponentsBuilder.newInstance().scheme("http").host("localhost").port(port)
                .path(BASE).path(separator < 0 ? suffix : suffix.substring(0, separator))
                .query(separator < 0 ? null : suffix.substring(separator + 1)).build().encode().toUri();
        try {
            return new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory())
                    .exchange(uri, method, new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }

    private HttpHeaders headers(UserRole role) {
        var headers = new HttpHeaders();
        if (role == null) return headers;
        String id = "question-publisher";
        mongoTemplate.getCollection("users").replaceOne(new Document("_id", id), new Document("_id", id)
                .append("email", "publisher@example.test").append("password_hash", "unused").append("role", role.name()).append("locked", false),
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        String session = UUID.randomUUID().toString();
        mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", session).append("account_id", id)
                .append("expires_at", Date.from(Instant.now().plusSeconds(3600))).append("revoked", false));
        headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + tokens.create(id, session, "access", 900));
        return headers;
    }

    private void authenticate(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("question-publisher", "publisher@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private List<String> codes(JsonNode data, String field) {
        var result = new ArrayList<String>();
        data.path(field).forEach(issue -> result.add(issue.path("code").asString()));
        return result;
    }

    private JsonNode data(ResponseEntity<String> response, int status) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        return json.readTree(response.getBody()).path("data");
    }

    private void assertError(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(json.readTree(response.getBody()).path("code").asString()).isEqualTo(code);
        assertThat(response.getBody()).doesNotContain("provider secret", "database secret");
    }
}
