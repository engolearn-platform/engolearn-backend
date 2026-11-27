package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.*;
import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import fit.iuh.engolearn.learningpath.repository.*;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class QuestionPoolQuestionLifecycleIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/learning-paths/questions";
    private static final String MICRO = "Reading for Gist";
    private static final Set<QuestionReviewCheck> CHECKS = EnumSet.complementOf(EnumSet.of(
            QuestionReviewCheck.AUDIO_MATCHES_TRANSCRIPT, QuestionReviewCheck.AUDIO_QUALITY_REVIEWED));
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private QuestionPoolQuestionManagementService service;
    @Autowired private QuestionPoolPassageManagementService passageService;
    @MockitoSpyBean private QuestionPoolQuestionRepository questions;
    @MockitoSpyBean private QuestionPoolCoverageRepository coverageRepository;
    @MockitoSpyBean private QuestionPoolPassageRepository passages;
    @MockitoBean private MediaManagement media;

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void retireBelowMinimumRequiresExplicitConfirmationThenPreservesContentAndLearnerData(UserRole role) {
        seedBank(30);
        mongoTemplate.getCollection("learning_path_questions").insertOne(new Document("question_id", "old").append("prompt", "Pinned"));
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("sentinel", "progress"));
        var before = snapshot();
        var response = request("/old/retire", retire(3, false), role);
        assertError(response, 409, "QUESTION_COVERAGE_CONFIRMATION_REQUIRED");
        var warning = json.readTree(response.getBody());
        assertThat(warning.path("confirmationRequired").asBoolean()).isTrue();
        assertThat(warning.path("warnings").get(0).path("coverage").path("activeQuestionCountAfter").asInt()).isEqualTo(29);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(mongoTemplate.getCollection("question_pool_coverage_fences").find().first().get("revision", Number.class).longValue()).isZero();
        var original = question("old");
        var data = data(request("/old/retire", retire(3, true), role));
        assertThat(data.path("status").asString()).isEqualTo("RETIRED");
        assertThat(data.path("revision").asInt()).isEqualTo(4);
        assertThat(data.path("retiredBy").asString()).isEqualTo("question-lifecycle-editor");
        assertThat(data.path("coverage").path("activeQuestionCountBefore").asInt()).isEqualTo(30);
        assertThat(data.path("coverage").path("activeQuestionCountAfter").asInt()).isEqualTo(29);
        assertThat(data.path("coverage").path("eligibleAfter").asBoolean()).isFalse();
        assertThat(data.path("warnings").get(0).path("code").asString()).isEqualTo("BELOW_MINIMUM_ACTIVE_QUESTIONS");
        for (String field : List.of("prompt", "options", "correct_option_id", "explanation", "publication_review", "created_at", "created_by", "import_marker")) {
            assertThat(question("old").get(field)).isEqualTo(original.get(field));
        }
        assertThat(mongoTemplate.getCollection("learning_path_questions").find().first().getString("prompt")).isEqualTo("Pinned");
        assertError(request("/old/retire", retire(3, true), role), 409, "QUESTION_WRITE_CONFLICT");
        verifyNoInteractions(media);
    }

    @Test
    void retirementAtThirtyRemainingNeedsNoCoverageAcknowledgementAndCountsOnlyTheExactCohort() {
        seedBank(31);
        seedQuestion("draft", "DRAFT", MICRO, "B1");
        seedQuestion("retired", "RETIRED", MICRO, "B1");
        seedQuestion("other-level", "ACTIVE", MICRO, "A2");
        seedQuestion("other-micro", "ACTIVE", "Reading for Specific Information", "B1");
        var data = data(request("/old/retire", "{\"revision\":3}", UserRole.ADMIN));
        assertThat(data.path("coverage").path("activeQuestionCountAfter").asInt()).isEqualTo(30);
        assertThat(data.path("coverage").path("eligibleAfter").asBoolean()).isTrue();
        assertThat(data.path("warnings").size()).isZero();
    }

    @Test
    void alreadyInsufficientCohortStillAllowsRetirementAfterConfirmation() {
        seedBank(1);
        assertError(request("/old/retire", retire(3, false), UserRole.ADMIN), 409, "QUESTION_COVERAGE_CONFIRMATION_REQUIRED");
        assertThat(data(request("/old/retire", retire(3, true), UserRole.ADMIN)).path("coverage").path("activeQuestionCountAfter").asInt()).isZero();
    }

    @Test
    void brokenQuestionContentDoesNotPreventRetirementOrTriggerMediaResolution() {
        seedBank(1);
        setQuestion("old", new Document("skill", "BROKEN").append("options", new Document("invalid", true))
                .append("passage_id", "missing-media-passage"));
        var response = request("/old/retire", retire(3, true), UserRole.ADMIN);
        data(response);
        assertThat(question("old").getString("status")).isEqualTo("RETIRED");
        assertThat(question("old").getString("skill")).isEqualTo("BROKEN");
        assertThat(question("old").get("options")).isEqualTo(new Document("invalid", true));
        verifyNoInteractions(media);
    }

    @Test
    void unclassifiableSourceRequiresWarningAcknowledgementWithoutInventingCoverageCounts() {
        seedBank(1);
        setQuestion("old", new Document("micro_skill", List.of(MICRO)).append("level", "INVALID"));
        assertError(request("/old/retire", retire(3, false), UserRole.ADMIN), 409, "QUESTION_COVERAGE_CONFIRMATION_REQUIRED");
        var data = data(request("/old/retire", retire(3, true), UserRole.ADMIN));
        assertThat(data.path("coverage").path("activeQuestionCountAfter").isNull()).isTrue();
        assertThat(data.path("coverage").path("eligibleAfter").isNull()).isTrue();
        assertThat(data.path("warnings").get(0).path("code").asString()).isEqualTo("COVERAGE_UNDETERMINED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "RETIRED"})
    void onlyActiveCurrentRevisionCanBeRetiredOrReplaced(String status) {
        seedBank(1);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        setQuestion("old", new Document("status", status));
        var before = snapshot();
        assertError(request("/old/retire", retire(3, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request("/old/replace", replacement("new", 3, 3, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void missingStaleExhaustedAndMalformedPublicIdentifiersFailWithoutMutation() {
        seedBank(1);
        assertError(request("/absent/retire", retire(3, true), UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        assertError(request("/old/retire", retire(2, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        setQuestion("old", new Document("revision", Integer.MAX_VALUE));
        assertError(request("/old/retire", retire(Integer.MAX_VALUE, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        setQuestion("old", new Document("revision", 3).append("question_id", List.of("old")));
        assertError(request("/old/retire", retire(3, true), UserRole.ADMIN), 400, "INVALID_QUESTION");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void replacementPublishesAndRetiresAtomicallyWithFreshReviewAndNoCoverageLoss(UserRole role) {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        var old = question("old");
        var data = data(request("/old/replace", replacement("new", 3, 3, false), role));
        assertThat(data.path("retiredQuestion").path("questionId").asString()).isEqualTo("old");
        assertThat(data.path("retiredQuestion").path("revision").asInt()).isEqualTo(4);
        assertThat(data.path("publishedQuestion").path("questionId").asString()).isEqualTo("new");
        assertThat(data.path("publishedQuestion").path("reviewedRevision").asInt()).isEqualTo(3);
        assertThat(data.path("coverage").size()).isEqualTo(1);
        assertThat(data.path("coverage").get(0).path("activeQuestionCountAfter").asInt()).isEqualTo(30);
        assertThat(data.path("warnings").size()).isZero();
        assertThat(question("old").getString("status")).isEqualTo("RETIRED");
        assertThat(question("new").getString("status")).isEqualTo("ACTIVE");
        assertThat(question("old").get("publication_review")).isEqualTo(old.get("publication_review"));
        assertThat(question("new").get("publication_review", Document.class).getInteger("source_revision")).isEqualTo(3);
        assertThat(question("old").getDate("updated_at")).isEqualTo(question("new").getDate("updated_at"));
        assertError(request("/old/replace", replacement("new", 3, 3, false), role), 409, "QUESTION_WRITE_CONFLICT");
        verifyNoInteractions(media);
    }

    @Test
    void replacementWithinInsufficientCohortDoesNotRequireAcknowledgementForUnchangedCount() {
        seedBank(1);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        var data = data(request("/old/replace", replacement("new", 3, 3, false), UserRole.ADMIN));
        assertThat(data.path("coverage").get(0).path("activeQuestionCountBefore").asInt()).isEqualTo(1);
        assertThat(data.path("coverage").get(0).path("activeQuestionCountAfter").asInt()).isEqualTo(1);
        assertThat(data.path("warnings").size()).isEqualTo(1);
    }

    @Test
    void replacementAcrossLevelsRequiresConfirmationOfCoverageLossAndRollsBackUntilConfirmed() {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "A2");
        var before = snapshot();
        assertError(request("/old/replace", replacement("new", 3, 3, false), UserRole.ADMIN), 409, "QUESTION_COVERAGE_CONFIRMATION_REQUIRED");
        assertThat(snapshot()).isEqualTo(before);
        var data = data(request("/old/replace", replacement("new", 3, 3, true), UserRole.ADMIN));
        assertThat(data.path("coverage").size()).isEqualTo(2);
        assertThat(data.path("coverage").get(0).path("activeQuestionCountAfter").asInt()).isEqualTo(29);
        assertThat(data.path("coverage").get(1).path("activeQuestionCountAfter").asInt()).isEqualTo(1);
        assertThat(data.path("warnings").size()).isEqualTo(2);
    }

    @Test
    void replacementRejectsSelfMissingOrNonDraftReplacementAndStaleRevisions() {
        seedBank(1);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        assertError(request("/old/replace", replacement("old", 3, 3, true), UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertError(request("/old/replace", replacement("absent", 3, 3, true), UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        assertError(request("/old/replace", replacement("new", 2, 3, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request("/old/replace", replacement("new", 3, 2, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        setQuestion("new", new Document("status", "ACTIVE"));
        assertError(request("/old/replace", replacement("new", 3, 3, true), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(question("old").getString("status")).isEqualTo("ACTIVE");
    }

    @Test
    void replacementReusesReadinessAndProfessionalReview() {
        seedBank(1);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        var withoutReview = json.writeValueAsString(new ReplaceQuestionPoolQuestionRequest(3, "new", 3, Set.of(), true));
        assertError(request("/old/replace", withoutReview, UserRole.ADMIN), 422, "QUESTION_REVIEW_REQUIRED");
        setQuestion("new", new Document("explanation", " "));
        assertError(request("/old/replace", replacement("new", 3, 3, true), UserRole.ADMIN), 422, "QUESTION_NOT_READY");
        assertThat(question("old").getString("status")).isEqualTo("ACTIVE");
        assertThat(question("new").getString("status")).isEqualTo("DRAFT");
    }

    @Test
    void replacementAcceptsManagedListeningWithFullProfessionalReview() {
        seedBank(1);
        seedQuestion("new", "DRAFT", "Listening for Gist", "B1");
        setQuestion("new", new Document("skill", "LISTENING").append("passage_id", "p"));
        seedPassage();
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", "p"),
                new Document("$set", new Document("skill", "LISTENING").append("text", null)
                        .append("transcript", "Transcript").append("audio_media_id", "media-id")));
        when(media.resolve("media-id")).thenReturn(new fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto(
                java.net.URI.create("https://example.test/audio.mp3"), Instant.now().plusSeconds(60), "audio/mpeg"));
        var body = json.writeValueAsString(new ReplaceQuestionPoolQuestionRequest(3, "new", 3,
                EnumSet.allOf(QuestionReviewCheck.class), true));
        var response = request("/old/replace", body, UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(question("new").getString("status")).isEqualTo("ACTIVE");
        assertThat(question("old").getString("status")).isEqualTo("RETIRED");
        assertThat(question("new").get("publication_review", Document.class).getList("confirmed_checks", String.class)).hasSize(9);
        assertThat(question("new").toJson()).doesNotContain("https://example.test/audio.mp3");
    }

    @Test
    void replacementFailureAfterNewPublicationRollsBackQuestionReviewPassageAndCoverageFence() {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        seedPassage();
        setQuestion("new", new Document("passage_id", "p"));
        var before = snapshot();
        doReturn(Optional.empty()).when(questions).retireActive(eq("old"), eq(3), anyString(), any());
        assertError(request("/old/replace", replacement("new", 3, 3, false), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void replacementInfrastructureFailureAfterNewPublicationRollsBackEverything() {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        var before = snapshot();
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).retireActive(eq("old"), eq(3), anyString(), any());
        assertError(request("/old/replace", replacement("new", 3, 3, true), UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void retirementStorageFailureDoesNotBypassConfirmationOrChangeSource() {
        seedBank(1);
        var before = snapshot();
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).retireActive(eq("old"), eq(3), anyString(), any());
        assertError(request("/old/retire", retire(3, true), UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"revision\":0}", "{\"revision\":2147483648}",
            "{\"revision\":3,\"confirmCoverageWarning\":\"unknown\"}"})
    void malformedRetirementOrReplacementReturns400(String body) {
        assertError(request("/old/retire", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(request("/old/replace", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
    }

    @Test
    void endpointsValidateIdentifiersReplacementShapeAndJsonContentType() {
        for (String id : List.of(" ", "x".repeat(101))) {
            assertError(request("/" + id + "/retire", retire(3, true), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
            assertError(request("/" + id + "/replace", replacement("new", 3, 3, true), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        assertError(request("/old/replace", replacement(" ", 3, 3, true), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(request("/old/replace", replacement("new", 3, 0, true), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(request("/old/replace", "{\"revision\":3,\"replacementQuestionId\":\"new\",\"replacementRevision\":3,\"confirmedReviewChecks\":[\"UNKNOWN\"]}", UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(request("/old/retire", "{}", UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(request("/old/replace", "{}", UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void bothHttpAndServiceProxyRequireManagerAuthority() {
        seedBank(1);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        for (UserRole role : new UserRole[]{null, UserRole.USER}) {
            int status = role == null ? 401 : 403;
            String code = role == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
            assertError(request("/old/retire", retire(3, true), role), status, code);
            assertError(request("/old/replace", replacement("new", 3, 3, true), role), status, code);
        }
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.retire("old", new RetireQuestionPoolQuestionRequest(3, true))).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.replace("old", new ReplaceQuestionPoolQuestionRequest(3, "new", 3, CHECKS, true))).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.retire("old", new RetireQuestionPoolQuestionRequest(3, true))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.replace("old", new ReplaceQuestionPoolQuestionRequest(3, "new", 3, CHECKS, true))).isInstanceOf(AccessDeniedException.class);
            authenticate(UserRole.CONTENT_MANAGER);
            assertThat(service.replace("old", new ReplaceQuestionPoolQuestionRequest(3, "new", 3, CHECKS, false)).publishedQuestion().revision()).isEqualTo(4);
            authenticate(UserRole.ADMIN);
            assertThat(service.retire("new", new RetireQuestionPoolQuestionRequest(4, true)).revision()).isEqualTo(5);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void concurrentRetirementsCannotBothSkipTheBelowThirtyConfirmation() throws Exception {
        seedBank(31);
        headers(UserRole.ADMIN);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            barrier.await(15, TimeUnit.SECONDS);
            return result;
        }).when(questions).findReadinessSource(argThat(id -> "old".equals(id) || "support-0".equals(id)));
        ResponseEntity<String> a, b;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> request("/old/retire", retire(3, false), UserRole.ADMIN));
            var second = executor.submit(() -> request("/support-0/retire", retire(3, false), UserRole.ADMIN));
            a = first.get(25, TimeUnit.SECONDS); b = second.get(25, TimeUnit.SECONDS);
            assertThat(List.of(a.getStatusCode().value(), b.getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        }
        doCallRealMethod().when(questions).findReadinessSource(anyString());
        String pending = a.getStatusCode().value() == 409 ? "old" : "support-0";
        assertError(request("/" + pending + "/retire", retire(3, false), UserRole.ADMIN), 409, "QUESTION_COVERAGE_CONFIRMATION_REQUIRED");
        assertThat(data(request("/" + pending + "/retire", retire(3, true), UserRole.ADMIN)).path("coverage").path("activeQuestionCountAfter").asInt()).isEqualTo(29);
    }

    @Test
    void concurrentReplacementOfSameSourceHasOneWinnerAndNoPartiallyPublishedLoser() throws Exception {
        seedBank(30);
        seedQuestion("new-a", "DRAFT", MICRO, "B1");
        seedQuestion("new-b", "DRAFT", MICRO, "B1");
        headers(UserRole.ADMIN);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            var result = invocation.callRealMethod(); barrier.await(15, TimeUnit.SECONDS); return result;
        }).when(questions).findReadinessSource("old");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> request("/old/replace", replacement("new-a", 3, 3, false), UserRole.ADMIN));
            var b = executor.submit(() -> request("/old/replace", replacement("new-b", 3, 3, false), UserRole.ADMIN));
            assertThat(List.of(a.get(25, TimeUnit.SECONDS).getStatusCode().value(), b.get(25, TimeUnit.SECONDS).getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(List.of(question("new-a").getString("status"), question("new-b").getString("status"))).containsExactlyInAnyOrder("ACTIVE", "DRAFT");
        assertThat(question("old").getString("status")).isEqualTo("RETIRED");
    }

    @Test
    void concurrentPublicationParticipatesInTheCoverageFence() throws Exception {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            if (first.getAndSet(false)) {
                entered.countDown();
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Fence wait timed out");
            }
            return invocation.callRealMethod();
        }).when(coverageRepository).fence(any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var retiring = executor.submit(() -> request("/old/retire", retire(3, false), UserRole.ADMIN));
            try {
                assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
                var publish = json.writeValueAsString(new PublishQuestionPoolQuestionRequest(3, CHECKS));
                data(request("/new/publish", publish, UserRole.ADMIN));
                assertThat(mongoTemplate.getCollection("question_pool").countDocuments(new Document("status", "ACTIVE")
                        .append("micro_skill", MICRO).append("level", "B1"))).isEqualTo(31);
                verify(coverageRepository, times(2)).fence(new QuestionPoolCoverageKey(MICRO, fit.iuh.engolearn.shared.enums.CefrLevel.B1));
            } finally { release.countDown(); }
            assertError(retiring.get(25, TimeUnit.SECONDS), 409, "QUESTION_WRITE_CONFLICT");
        }
        var data = data(request("/old/retire", retire(3, false), UserRole.ADMIN));
        assertThat(data.path("coverage").path("activeQuestionCountAfter").asInt()).isEqualTo(30);
    }

    @Test
    void concurrentPassageRetirementPreventsPartialReplacement() throws Exception {
        seedBank(30);
        seedQuestion("new", "DRAFT", MICRO, "B1");
        seedPassage();
        setQuestion("new", new Document("passage_id", "p"));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Fence wait timed out");
            return invocation.callRealMethod();
        }).when(passages).fenceQuestionPublication(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var replacing = executor.submit(() -> request("/old/replace", replacement("new", 3, 3, false), UserRole.ADMIN));
            try {
                assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
                authenticate(UserRole.ADMIN);
                passageService.retire("p", new RetireQuestionPoolPassageRequest(1));
            } finally { SecurityContextHolder.clearContext(); release.countDown(); }
            assertError(replacing.get(25, TimeUnit.SECONDS), 409, "QUESTION_WRITE_CONFLICT");
        }
        assertThat(question("old").getString("status")).isEqualTo("ACTIVE");
        assertThat(question("new").getString("status")).isEqualTo("DRAFT");
        assertThat(question("new").get("publication_review")).isNull();
    }

    private void seedBank(int active) {
        seedQuestion("old", "ACTIVE", MICRO, "B1");
        setQuestion("old", new Document("publication_review", new Document("reviewed_by", "original-editor")));
        for (int i = 0; i < active - 1; i++) seedQuestion("support-" + i, "ACTIVE", MICRO, "B1");
    }

    private void seedQuestion(String id, String status, String micro, String level) {
        mongoTemplate.getCollection("question_pool").insertOne(new Document("question_id", id).append("status", status).append("revision", 3)
                .append("skill", "READING").append("micro_skill", micro).append("level", level).append("prompt", "Prompt")
                .append("options", List.of("A", "B", "C", "D").stream().map(value -> new Document("option_id", value).append("text", "Option " + value)).toList())
                .append("correct_option_id", "B").append("explanation", "Evidence").append("created_by", "author")
                .append("created_at", Date.from(Instant.parse("2026-11-26T01:00:00Z"))).append("updated_at", Date.from(Instant.parse("2026-11-26T01:00:00Z")))
                .append("import_marker", "preserve"));
    }

    private void seedPassage() {
        mongoTemplate.getCollection("question_pool_passages").insertOne(new Document("passage_id", "p").append("status", "ACTIVE")
                .append("revision", 1).append("skill", "READING").append("level", "B1").append("title", "Title").append("text", "Text"));
    }

    private Document question(String id) { return mongoTemplate.getCollection("question_pool").find(new Document("question_id", id)).first(); }
    private void setQuestion(String id, Document values) { mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", id), new Document("$set", values)); }
    private List<List<Document>> snapshot() {
        return List.of("question_pool", "question_pool_passages", "question_pool_coverage_fences", "learning_paths", "learning_path_questions", "media_assets").stream()
                // Revision-zero allocation is allowed; a rejected mutation must not commit any fence increment.
                .map(name -> (List<Document>) mongoTemplate.getCollection(name).find(name.equals("question_pool_coverage_fences")
                        ? new Document("revision", new Document("$gt", 0)) : new Document()).into(new ArrayList<Document>())).toList();
    }
    private String retire(int revision, boolean confirm) { return json.writeValueAsString(new RetireQuestionPoolQuestionRequest(revision, confirm)); }
    private String replacement(String id, int revision, int newRevision, boolean confirm) {
        return json.writeValueAsString(new ReplaceQuestionPoolQuestionRequest(revision, id, newRevision, CHECKS, confirm));
    }
    private ResponseEntity<String> request(String suffix, String body, UserRole role) { return request(suffix, body, role, MediaType.APPLICATION_JSON); }
    private ResponseEntity<String> request(String suffix, String body, UserRole role, MediaType type) {
        var headers = headers(role); headers.setContentType(type);
        var uri = org.springframework.web.util.UriComponentsBuilder.newInstance().scheme("http").host("localhost").port(port).path(BASE).path(suffix).build().encode().toUri();
        try {
            return new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory()).exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }
    private HttpHeaders headers(UserRole role) {
        var headers = new HttpHeaders();
        if (role == null) return headers;
        String id = "question-lifecycle-editor";
        mongoTemplate.getCollection("users").replaceOne(new Document("_id", id), new Document("_id", id).append("email", "lifecycle@example.test")
                .append("password_hash", "unused").append("role", role.name()).append("locked", false), new com.mongodb.client.model.ReplaceOptions().upsert(true));
        String session = UUID.randomUUID().toString();
        mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", session).append("account_id", id)
                .append("expires_at", Date.from(Instant.now().plusSeconds(3600))).append("revoked", false));
        headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + tokens.create(id, session, "access", 900));
        return headers;
    }
    private void authenticate(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("question-lifecycle-editor", "lifecycle@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private JsonNode data(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        return json.readTree(response.getBody()).path("data");
    }
    private void assertError(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(json.readTree(response.getBody()).path("code").asString()).as(response.getBody()).isEqualTo(code);
        assertThat(response.getBody()).doesNotContain("database secret");
    }
}
