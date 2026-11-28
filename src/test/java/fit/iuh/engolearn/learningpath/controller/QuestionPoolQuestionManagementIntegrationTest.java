package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
class QuestionPoolQuestionManagementIntegrationTest extends AbstractMongoIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void seedMicroSkills() { fit.iuh.engolearn.microskill.MicroSkillFixtures.seed(mongoTemplate); }

    private static final String BASE = "/api/management/learning-paths/questions";
    private static final String MICRO_SKILL = "Reading for Gist";
    private static final String CREATE = """
            {"skill":"READING","microSkill":"Reading for Gist","level":"B1","prompt":"  Prompt  ",
             "options":[{"optionId":"A","text":" Option A "},{"optionId":"B","text":"Option B"},
             {"optionId":"C","text":"Option C"},{"optionId":"D","text":"Option D"}],
             "correctOptionId":"B","explanation":"  Evidence  "}
            """;
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private QuestionPoolQuestionManagementService service;
    @Autowired private fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService passageService;
    @MockitoSpyBean private QuestionPoolQuestionRepository questions;
    @MockitoSpyBean private QuestionPoolPassageRepository passageRepository;
    @MockitoBean private MediaManagement media;

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void createsDraftWithTrustedAuditAndEditableContent(UserRole role) {
        var response = request(HttpMethod.POST, "", CREATE, role);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        var data = json.readTree(response.getBody()).path("data");
        String id = data.path("questionId").asString();
        assertThat(id).startsWith("LP-Q-");
        var saved = question(id);
        assertThat(saved.getString("status")).isEqualTo("DRAFT");
        assertThat(saved.getInteger("revision")).isEqualTo(1);
        assertThat(saved.getString("created_by")).isEqualTo("question-editor");
        assertThat(saved.getString("updated_by")).isEqualTo("question-editor");
        assertThat(saved.getString("prompt")).isEqualTo("Prompt");
        assertThat(saved.getString("explanation")).isEqualTo("Evidence");
        assertThat(saved.getList("options", Document.class).getFirst().getString("option_id")).isEqualTo("A");
        assertThat(saved.getList("options", Document.class).getFirst().getString("text")).isEqualTo("Option A");
        assertThat(saved.get("passage_id")).isNull();
        assertThat(saved.getDate("created_at")).isNotNull();
        assertThat(saved.getDate("updated_at")).isNotNull();
        assertNoLearnerOrMediaWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "RETIRED"})
    void listeningDraftCanReferenceExistingMatchingPassageWithoutResolvingAudio(String status) {
        seedPassage(status, "LISTENING", new Document("transcript", "Transcript").append("audio_media_id", "unavailable"));
        var before = mongoTemplate.getCollection("question_pool_passages").find().first();
        var response = request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        var saved = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(saved.getInteger("revision")).isEqualTo(2);
        assertThat(saved.getString("status")).isEqualTo(status);
        assertThat(saved.getString("transcript")).isEqualTo(before.getString("transcript"));
        assertThat(saved.getString("audio_media_id")).isEqualTo("unavailable");
        assertThat(saved.getString("updated_by")).isEqualTo("question-editor");
        assertThat(mongoTemplate.getCollection("question_pool").find().first().getString("passage_id")).isEqualTo("p");
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null",
            "{\"skill\":\"SPEAKING\"}",
            "{\"revision\":0}"})
    void malformedCreationDoesNotWrite(String body) {
        assertError(request(HttpMethod.POST, "", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    @Test
    void createValidatesCanonicalSkillOptionIdsAndListeningReference() {
        for (String body : List.of(
                CREATE.replace("Reading for Gist", "Unknown skill"),
                CREATE.replace("Reading for Gist", "Listening for Gist"),
                CREATE.replace("\"optionId\":\"D\"", "\"optionId\":\"A\""),
                CREATE.replace("\"skill\":\"READING\"", "\"skill\":\"LISTENING\"").replace("Reading for Gist", "Listening for Gist"))) {
            assertError(request(HttpMethod.POST, "", body, UserRole.ADMIN), 400, "INVALID_QUESTION");
        }
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
        assertNoLearnerOrMediaWrites();
    }

    @Test
    void rejectsEmptyOrOversizedContentInvalidChoicesAndMissingFields() {
        for (String body : List.of(
                CREATE.replace("  Prompt  ", " "),
                CREATE.replace("  Evidence  ", " "),
                CREATE.replace("Option B", " "),
                CREATE.replace("  Prompt  ", "x".repeat(50001)),
                CREATE.replace("Option B", "x".repeat(10001)),
                CREATE.replace("\"correctOptionId\":\"B\"", "\"correctOptionId\":\"E\""),
                CREATE.replace("{\"optionId\":\"D\",\"text\":\"Option D\"}", "null"),
                CREATE.replace("\"level\":\"B1\",", ""))) {
            assertError(request(HttpMethod.POST, "", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    @Test
    void missingMismatchedOrUnversionedPassageCannotReceiveAReference() {
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 400, "INVALID_QUESTION");
        seedPassage("DRAFT", "READING", new Document("text", "Text"));
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 400, "INVALID_QUESTION");
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", "p"),
                new Document("$set", new Document("skill", "LISTENING").append("level", "A2")));
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 400, "INVALID_QUESTION");
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", "p"),
                new Document("$set", new Document("level", "B1").append("revision", Integer.MAX_VALUE)));
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    @Test
    void patchMergesFieldsPreservesCreatorAndUnknownMetadataAndCanDetachReadingPassage() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, "p", "Prompt", 0);
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"),
                new Document("$set", new Document("import_marker", "preserve")));
        var before = question("q");
        var response = request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\" New prompt \",\"passageId\":\"\"}", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        var saved = question("q");
        assertThat(saved.getString("prompt")).isEqualTo("New prompt");
        assertThat(saved.get("passage_id")).isNull();
        assertThat(saved.getInteger("revision")).isEqualTo(4);
        assertThat(saved.getString("updated_by")).isEqualTo("question-editor");
        assertThat(saved.getString("created_by")).isEqualTo("author");
        assertThat(saved.getDate("created_at")).isEqualTo(before.getDate("created_at"));
        assertThat(saved.get("options")).isEqualTo(before.get("options"));
        assertThat(saved.getString("import_marker")).isEqualTo("preserve");
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"Lost\"}", UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
    }

    @Test
    void changingClassificationRechecksMergedMicroSkillAndPassage() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"skill\":\"LISTENING\"}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        seedPassage("DRAFT", "LISTENING", new Document("transcript", "Transcript"));
        var response = request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"skill\":\"LISTENING\",\"microSkill\":\"Listening for Gist\",\"passageId\":\"p\"}", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(question("q").getString("skill")).isEqualTo("LISTENING");
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":4,\"passageId\":\"\"}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":4,\"level\":\"A2\"}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertThat(question("q").getInteger("revision")).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RETIRED"})
    void publishedAndRetiredQuestionsCannotBeEditedOrDeleted(String status) {
        seedQuestion("q", status, "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        var before = question("q");
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"New\"}", UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertError(request(HttpMethod.DELETE, "/q?revision=3", null, UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(question("q")).isEqualTo(before);
    }

    @Test
    void patchRejectsNoopBlankContentExhaustedRevisionAndMissingQuestion() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"explanation\":\" \"}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertError(request(HttpMethod.PATCH, "/absent", "{\"revision\":3,\"prompt\":\"New\"}", UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"), new Document("$set", new Document("revision", Integer.MAX_VALUE)));
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":2147483647,\"prompt\":\"New\"}", UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
    }

    @Test
    void draftDeletionRequiresCurrentRevisionAndDoesNotTouchLearnerSnapshots() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        mongoTemplate.getCollection("learning_path_questions").insertOne(new Document("question_id", "q").append("prompt", "Pinned"));
        var snapshot = mongoTemplate.getCollection("learning_path_questions").find().first();
        assertError(request(HttpMethod.DELETE, "/q?revision=2", null, UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        var response = request(HttpMethod.DELETE, "/q?revision=3", null, UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response).path("deleted").asBoolean()).isTrue();
        assertThat(question("q")).isNull();
        assertThat(mongoTemplate.getCollection("learning_path_questions").find().first()).isEqualTo(snapshot);
        assertError(request(HttpMethod.DELETE, "/q?revision=3", null, UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "RETIRED"})
    void copyCreatesIndependentDraftWithoutCarryingReviewOrSourceAudit(String status) {
        seedQuestion("q", status, "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"),
                new Document("$set", new Document("publication_review", new Document("reviewed_by", "old-editor"))));
        var source = question("q");
        var response = request(HttpMethod.POST, "/q/copy", "{\"revision\":3}", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String id = json.readTree(response.getBody()).path("data").path("questionId").asString();
        assertThat(id).isNotEqualTo("q");
        var copied = question(id);
        assertThat(copied.getString("status")).isEqualTo("DRAFT");
        assertThat(copied.getInteger("revision")).isEqualTo(1);
        assertThat(copied.get("options")).isEqualTo(source.get("options"));
        assertThat(copied.getString("correct_option_id")).isEqualTo("B");
        assertThat(copied.getString("created_by")).isEqualTo("question-editor");
        assertThat(copied.get("publication_review")).isNull();
        assertThat(question("q")).isEqualTo(source);
        assertError(request(HttpMethod.POST, "/q/copy", "{\"revision\":2}", UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
    }

    @Test
    void copyRejectsIncompleteSourceWithoutInventingAnAnswer() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"),
                new Document("$unset", new Document("options", "").append("correct_option_id", "")));
        assertError(request(HttpMethod.POST, "/q/copy", "{\"revision\":3}", UserRole.ADMIN), 400, "INVALID_QUESTION");
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isEqualTo(1);
        assertThat(question("q").get("correct_option_id")).isNull();
        assertError(request(HttpMethod.POST, "/absent/copy", "{\"revision\":3}", UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
    }

    @Test
    void mutationsRejectMissingInvalidOrMalformedRevisionAndUnsupportedContentType() {
        for (String body : List.of("{}", "{\"revision\":0}", "{\"revision\":-1}", "{\"revision\":2147483648}", "{")) {
            assertError(request(HttpMethod.PATCH, "/q", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
            assertError(request(HttpMethod.POST, "/q/copy", body, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        for (String query : List.of("", "?revision=0", "?revision=bad")) {
            assertError(request(HttpMethod.DELETE, "/q" + query, null, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        assertError(request(HttpMethod.POST, "", CREATE, UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(request(HttpMethod.PATCH, "/q", "{}", UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(request(HttpMethod.POST, "/q/copy", "{}", UserRole.ADMIN, MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void mutationHttpAndSpringProxyBoundariesRejectGuestAndLearner() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        for (UserRole role : new UserRole[]{null, UserRole.USER}) {
            int code = role == null ? 401 : 403;
            String error = role == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
            assertError(request(HttpMethod.POST, "", CREATE, role), code, error);
            assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"New\"}", role), code, error);
            assertError(request(HttpMethod.DELETE, "/q?revision=3", null, role), code, error);
            assertError(request(HttpMethod.POST, "/q/copy", "{\"revision\":3}", role), code, error);
        }
        var create = json.readValue(CREATE, CreateQuestionPoolQuestionRequest.class);
        var update = json.readValue("{\"revision\":3,\"prompt\":\"New\"}", UpdateQuestionPoolQuestionRequest.class);
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.create(create)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.update("q", update)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.delete("q", 3)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.copy("q", 3)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.create(create)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.update("q", update)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.delete("q", 3)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.copy("q", 3)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void insertFailureRollsBackPassageRevisionAndLeavesNoQuestion() {
        seedPassage("DRAFT", "LISTENING", new Document("transcript", "Transcript"));
        var before = mongoTemplate.getCollection("question_pool_passages").find().first();
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).insert(any());
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(before);
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    @Test
    void losingQuestionCasRollsBackPassageFenceInsteadOfLeavingPartialUpdate() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, "p", "Prompt", 0);
        seedPassage("DRAFT", "READING", new Document("text", "Text"));
        var before = mongoTemplate.getCollection("question_pool_passages").find().first();
        doReturn(java.util.Optional.empty()).when(questions).updateDraft(any(), eq(3));
        assertError(request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"New\"}", UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(before);
        assertThat(question("q").getString("prompt")).isEqualTo("Prompt");
    }

    @Test
    void passageCasLossRejectsCreationWithoutOrphanReference() {
        seedPassage("DRAFT", "LISTENING", new Document("transcript", "Transcript"));
        doReturn(false).when(passageRepository).fenceQuestionReference(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
        assertError(request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN), 409, "QUESTION_WRITE_CONFLICT");
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    @Test
    void concurrentQuestionEditsProduceOneWinnerAndOneRevisionConflict() throws Exception {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        assertThat(get("/q", UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        doAnswer(invocation -> {
            var source = invocation.callRealMethod();
            barrier.await(15, java.util.concurrent.TimeUnit.SECONDS);
            return source;
        }).when(questions).findByQuestionId("q");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"First\"}", UserRole.ADMIN).getStatusCode().value());
            var second = executor.submit(() -> request(HttpMethod.PATCH, "/q", "{\"revision\":3,\"prompt\":\"Second\"}", UserRole.ADMIN).getStatusCode().value());
            assertThat(List.of(first.get(25, java.util.concurrent.TimeUnit.SECONDS), second.get(25, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(question("q").getInteger("revision")).isEqualTo(4);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void authorizedServiceProxyCanCreateEditCopyAndDelete(UserRole role) {
        try {
            authenticate(role);
            var created = service.create(json.readValue(CREATE, CreateQuestionPoolQuestionRequest.class));
            var updated = service.update(created.questionId(), json.readValue("{\"revision\":1,\"prompt\":\"Updated\"}", UpdateQuestionPoolQuestionRequest.class));
            assertThat(updated.revision()).isEqualTo(2);
            var copied = service.copy(created.questionId(), 2);
            assertThat(copied.questionId()).isNotEqualTo(created.questionId());
            assertThat(service.delete(created.questionId(), 2).deleted()).isTrue();
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void patchReplacesAllChoicesUsingStoredOptionIdMapping() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        var body = CREATE.replaceFirst("\\{", "{\"revision\":3,").replace("\"correctOptionId\":\"B\"", "\"correctOptionId\":\"D\"");
        var response = request(HttpMethod.PATCH, "/q", body, UserRole.CONTENT_MANAGER);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(question("q").getString("correct_option_id")).isEqualTo("D");
        assertThat(question("q").getList("options", Document.class).getFirst().getString("option_id")).isEqualTo("A");
        assertThat(question("q").getList("options", Document.class).getFirst().getString("text")).isEqualTo("Option A");
    }

    @Test
    void concurrentPassageDeletionRejectsReferenceInsteadOfCreatingOrphan() throws Exception {
        referenceRace(false);
    }

    @Test
    void concurrentPassageClassificationEditRejectsIncompatibleReference() throws Exception {
        referenceRace(true);
    }

    private void referenceRace(boolean edit) throws Exception {
        seedPassage("DRAFT", "LISTENING", new Document("transcript", "Transcript").append("audio_media_id", "media-id"));
        var reachedFence = new java.util.concurrent.CountDownLatch(1);
        var releaseFence = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            reachedFence.countDown();
            if (!releaseFence.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Fence wait timed out");
            return invocation.callRealMethod();
        }).when(passageRepository).fenceQuestionReference(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var creating = executor.submit(() -> request(HttpMethod.POST, "", listeningCreate(), UserRole.ADMIN));
            try {
                assertThat(reachedFence.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                authenticate(UserRole.ADMIN);
                if (edit) {
                    passageService.update("p", new fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolPassageRequest(
                            1, null, fit.iuh.engolearn.shared.enums.CefrLevel.A2, null, null, null), null, null, null, 0);
                } else {
                    assertThat(passageService.delete("p", 1).deleted()).isTrue();
                }
            } finally {
                SecurityContextHolder.clearContext();
                releaseFence.countDown();
            }
            assertError(creating.get(25, java.util.concurrent.TimeUnit.SECONDS), 409, "QUESTION_WRITE_CONFLICT");
        }
        assertThat(mongoTemplate.getCollection("question_pool").countDocuments()).isZero();
    }

    private Document question(String id) {
        return mongoTemplate.getCollection("question_pool").find(new Document("question_id", id)).first();
    }

    private String listeningCreate() {
        return CREATE.replace("READING", "LISTENING").replace("Reading for Gist", "Listening for Gist")
                .replace("\"level\":\"B1\"", "\"level\":\"B1\",\"passageId\":\"p\"");
    }

    private void assertNoLearnerOrMediaWrites() {
        for (String name : List.of("learning_paths", "learning_path_questions", "media_assets")) {
            assertThat(mongoTemplate.getCollection(name).countDocuments()).as(name).isZero();
        }
        verifyNoInteractions(media);
    }

    @Test
    void defaultsListAllStatusesWithoutAnswersOrPassageResolution() {
        seedQuestion("a", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Library", 0);
        seedQuestion("b", "ACTIVE", "LISTENING", "A2", "Listening for Gist", "missing", "Bus", 1);
        seedQuestion("c", "RETIRED", "READING", "C1", MICRO_SKILL, null, "Closed", 2);
        var response = get("", UserRole.CONTENT_MANAGER);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var data = data(response);
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("totalElements").asInt()).isEqualTo(3);
        assertThat(data.path("totalPages").asInt()).isEqualTo(1);
        assertIds(data, "c", "b", "a");
        for (JsonNode item : data.path("items")) {
            for (String field : List.of("options", "correctOptionId", "explanation", "passage", "createdBy", "id")) {
                assertThat(item.has(field)).as(field).isFalse();
            }
        }
        verifyNoInteractions(media);
    }

    @Test
    void combinesAllFiltersAndSearchesPromptOrPublicIdLiterally() {
        seedQuestion("needle", "ACTIVE", "READING", "B1", MICRO_SKILL, "p", "Other", 0);
        seedQuestion("match", "ACTIVE", "READING", "B1", MICRO_SKILL, "p", "NEEDLE .* [", 1);
        seedQuestion("level", "ACTIVE", "READING", "A2", MICRO_SKILL, "p", "Needle", 2);
        seedQuestion("status", "DRAFT", "READING", "B1", MICRO_SKILL, "p", "Needle", 3);
        seedQuestion("micro", "ACTIVE", "READING", "B1", "Reading for Specific Information", "p", "Needle", 4);
        seedQuestion("passage", "ACTIVE", "READING", "B1", MICRO_SKILL, "other", "Needle", 5);
        seedQuestion("skill", "ACTIVE", "LISTENING", "B1", "Listening for Gist", "p", "Needle", 6);
        String filters = "?keyword= needle &skill=READING&microSkill= Reading for Gist &level=B1&status=ACTIVE&passageId= p &size=1";
        var first = data(get(filters, UserRole.ADMIN));
        assertIds(first, "match");
        assertThat(first.path("totalElements").asInt()).isEqualTo(2);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertIds(data(get(filters + "&page=1", UserRole.ADMIN)), "needle");
        assertIds(data(get("?keyword=.*", UserRole.ADMIN)), "match");
        assertIds(data(get("?keyword=[", UserRole.ADMIN)), "match");
        assertThat(data(get("?microSkill=reading for gist", UserRole.ADMIN)).path("totalElements").asInt()).isZero();
        assertThat(data(get("?keyword=   &microSkill= &passageId= ", UserRole.ADMIN)).path("totalElements").asInt()).isEqualTo(7);
        verifyNoInteractions(media);
    }

    @Test
    void paginationIsStableOnEqualDatesAndRetainsTotalsBeyondLastPage() {
        for (int i = 0; i < 5; i++) seedQuestion("same-" + i, "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        assertIds(data(get("?size=2&page=0", UserRole.ADMIN)), "same-4", "same-3");
        assertIds(data(get("?size=2&page=1", UserRole.ADMIN)), "same-2", "same-1");
        assertIds(data(get("?size=2&page=2", UserRole.ADMIN)), "same-0");
        var outside = data(get("?size=2&page=3", UserRole.ADMIN));
        assertIds(outside);
        assertThat(outside.path("totalElements").asInt()).isEqualTo(5);
        assertThat(outside.path("totalPages").asInt()).isEqualTo(3);
        assertIds(data(get("?size=100&page=2147483647", UserRole.ADMIN)));
    }

    @Test
    void emptyAndUnmatchedListsAreSuccessfulWithZeroTotals() {
        var empty = data(get("", UserRole.ADMIN));
        assertIds(empty);
        assertThat(empty.path("totalElements").asInt()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
        seedQuestion("a", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        var unmatched = data(get("?keyword=absent", UserRole.ADMIN));
        assertIds(unmatched);
        assertThat(unmatched.path("totalElements").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=no", "page=2147483648", "size=0", "size=101", "size=no",
            "skill=SPEAKING", "level=INVALID", "status=PUBLISHED"})
    void rejectsInvalidFilters(String query) {
        assertError(get("?" + query, UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
    }

    @Test
    void rejectsOverlongFiltersAndInvalidQuestionIds() {
        for (String field : List.of("keyword", "microSkill")) {
            assertError(get("?" + field + "=" + "x".repeat(201), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        }
        assertError(get("?passageId=" + "x".repeat(101), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(get("/" + "x".repeat(101), UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
        assertError(get("/   ", UserRole.ADMIN), 400, "INVALID_QUESTION_REQUEST");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void detailsReturnFourOptionsCorrectAnswerAndExplanationForManagers(UserRole role) {
        seedQuestion("q", "ACTIVE", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        var response = get("/q", role);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var question = data(response);
        assertThat(question.path("questionId").asString()).isEqualTo("q");
        assertThat(question.path("microSkill").asString()).isEqualTo(MICRO_SKILL);
        assertThat(question.path("options").size()).isEqualTo(4);
        assertThat(question.path("options").get(0).path("optionId").asString()).isEqualTo("A");
        assertThat(question.path("options").get(0).path("text").asString()).isEqualTo("Option A");
        assertThat(question.path("correctOptionId").asString()).isEqualTo("B");
        assertThat(question.path("explanation").asString()).isEqualTo("Evidence");
        assertThat(question.path("revision").asInt()).isEqualTo(3);
        assertThat(question.path("createdBy").asString()).isEqualTo("author");
        assertThat(question.path("passage").isNull()).isTrue();
        assertThat(question.has("id")).isFalse();
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "RETIRED"})
    void detailCanPreviewPassageInEveryState(String status) {
        seedQuestion("q", status, "READING", "B1", MICRO_SKILL, "p", "Prompt", 0);
        seedPassage(status, "READING", new Document("text", "Reading text"));
        var detail = data(get("/q", UserRole.ADMIN));
        assertThat(detail.path("passageId").asString()).isEqualTo("p");
        assertThat(detail.path("passage").path("text").asString()).isEqualTo("Reading text");
        assertThat(detail.path("passage").path("status").asString()).isEqualTo(status);
    }

    @Test
    void legacyListeningPreviewReturnsTranscriptAndLocalUrlUnchanged() {
        seedQuestion("q", "ACTIVE", "LISTENING", "B1", "Listening for Gist", "p", "Prompt", 0);
        seedPassage("ACTIVE", "LISTENING", new Document("transcript", "Transcript")
                .append("audio", new Document("url", "/learning-path-audio/local.mp3").append("duration_sec", 20).append("format", "mp3")));
        var passage = data(get("/q", UserRole.ADMIN)).path("passage");
        assertThat(passage.path("transcript").asString()).isEqualTo("Transcript");
        assertThat(passage.path("audio").path("url").asString()).isEqualTo("/learning-path-audio/local.mp3");
        verifyNoInteractions(media);
    }

    @Test
    void mediaPreviewResolvesFreshProtectedDeliveryWithoutPersistingUrlOrFallingBack() {
        seedQuestion("q", "DRAFT", "LISTENING", "B1", "Listening for Gist", "p", "Prompt", 0);
        seedPassage("DRAFT", "LISTENING", new Document("transcript", "Transcript").append("audio_media_id", "media-id")
                .append("audio", new Document("url", "/old.mp3")));
        when(media.resolve("media-id")).thenReturn(new MediaDeliveryDto(URI.create("https://example.test/signed.mp3"),
                Instant.now().plusSeconds(60), "audio/mpeg"));
        var audio = data(get("/q", UserRole.ADMIN)).path("passage").path("audio");
        assertThat(audio.path("url").asString()).isEqualTo("https://example.test/signed.mp3");
        assertThat(audio.path("expiresAt").asString()).isNotBlank();
        assertThat(audio.path("contentType").asString()).isEqualTo("audio/mpeg");
        verify(media).resolve("media-id");
        when(media.resolve("media-id")).thenThrow(new MediaNotFoundException("media-id"));
        assertError(get("/q", UserRole.ADMIN), 404, "MEDIA_NOT_FOUND");
        doThrow(new MediaStorageUnavailableException("provider secret")).when(media).resolve("media-id");
        assertError(get("/q", UserRole.ADMIN), 503, "MEDIA_STORAGE_UNAVAILABLE");
    }

    @Test
    void danglingPassageAndIncompleteDraftRemainInspectableWithoutSynthesizedContent() {
        seedQuestion("q", "DRAFT", "LISTENING", "B1", "Listening for Gist", "missing", "Prompt", 0);
        mongoTemplate.getCollection("question_pool").updateOne(new Document("question_id", "q"),
                new Document("$unset", new Document("options", "").append("revision", "").append("created_by", "")));
        var detail = data(get("/q", UserRole.ADMIN));
        assertThat(detail.path("passageId").asString()).isEqualTo("missing");
        assertThat(detail.path("passage").isNull()).isTrue();
        assertThat(detail.path("options").isNull()).isTrue();
        assertThat(detail.path("revision").isNull()).isTrue();
        assertThat(detail.path("createdBy").isNull()).isTrue();
        assertError(get("/absent", UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
        var id = mongoTemplate.getCollection("question_pool").find().first().getObjectId("_id").toHexString();
        assertError(get("/" + id, UserRole.ADMIN), 404, "QUESTION_NOT_FOUND");
    }

    @Test
    void httpAndServiceBoundariesEnforceManagerAuthorityIncludingAdminInheritance() {
        seedQuestion("q", "DRAFT", "READING", "B1", MICRO_SKILL, null, "Prompt", 0);
        for (String suffix : List.of("", "/q")) {
            assertError(get(suffix, null), 401, "AUTHENTICATION_REQUIRED");
            assertError(get(suffix, UserRole.USER), 403, "ACCESS_DENIED");
            assertThat(get(suffix, UserRole.CONTENT_MANAGER).getStatusCode().value()).isEqualTo(200);
            assertThat(get(suffix, UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var request = new ListQuestionPoolQuestionsRequest(null, null, null, null, null, null, null, null);
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.list(request)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThatThrownBy(() -> service.detail("q")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.list(request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.detail("q")).isInstanceOf(AccessDeniedException.class);
            for (UserRole role : List.of(UserRole.CONTENT_MANAGER, UserRole.ADMIN)) {
                authenticate(role);
                assertThat(service.list(request).totalElements()).isEqualTo(1);
                assertThat(service.detail("q").questionId()).isEqualTo("q");
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void databaseFailuresReturn503RatherThanEmptyResultsOrMissingQuestions() {
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions)
                .search(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        assertError(get("", UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
        doThrow(new DataAccessResourceFailureException("database secret")).when(questions).findByQuestionId("q");
        assertError(get("/q", UserRole.ADMIN), 503, "QUESTION_STORAGE_UNAVAILABLE");
    }

    @Test
    void readsPreserveSourcesMediaAndExistingLearnerSnapshotsExactly() {
        seedQuestion("q", "ACTIVE", "READING", "B1", MICRO_SKILL, "p", "Prompt", 0);
        seedPassage("ACTIVE", "READING", new Document("text", "Text"));
        for (String collection : List.of("learning_paths", "learning_path_questions", "media_assets")) {
            mongoTemplate.getCollection(collection).insertOne(new Document("sentinel", "preserve"));
        }
        var names = List.of("question_pool", "question_pool_passages", "learning_paths", "learning_path_questions", "media_assets");
        var before = names.stream().map(name -> mongoTemplate.getCollection(name).find().into(new java.util.ArrayList<>())).toList();
        assertThat(get("", UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/q", UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        var after = names.stream().map(name -> mongoTemplate.getCollection(name).find().into(new java.util.ArrayList<>())).toList();
        assertThat(after).isEqualTo(before);
        verifyNoInteractions(media);
    }

    private void seedQuestion(String id, String status, String skill, String level, String microSkill,
            String passageId, String prompt, int seconds) {
        mongoTemplate.getCollection("question_pool").insertOne(new Document("question_id", id)
                .append("status", status).append("skill", skill).append("level", level).append("micro_skill", microSkill)
                .append("passage_id", passageId).append("prompt", prompt).append("revision", 3)
                .append("options", List.of("A", "B", "C", "D").stream()
                        .map(option -> new Document("option_id", option).append("text", "Option " + option)).toList())
                .append("correct_option_id", "B").append("explanation", "Evidence").append("created_by", "author")
                .append("created_at", Date.from(Instant.parse("2026-11-26T01:00:00Z")))
                .append("updated_at", Date.from(Instant.parse("2026-11-26T01:00:00Z").plusSeconds(seconds))));
    }

    private void seedPassage(String status, String skill, Document content) {
        var passage = new Document("passage_id", "p").append("status", status).append("skill", skill)
                .append("level", "B1").append("title", "Title").append("revision", 1);
        passage.putAll(content);
        mongoTemplate.getCollection("question_pool_passages").insertOne(passage);
    }

    private ResponseEntity<String> get(String suffix, UserRole role) {
        return request(HttpMethod.GET, suffix, null, role);
    }

    private ResponseEntity<String> request(HttpMethod method, String suffix, String body, UserRole role) {
        return request(method, suffix, body, role, MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<String> request(HttpMethod method, String suffix, String body, UserRole role, MediaType contentType) {
        var client = new org.springframework.web.client.RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
        return exchange(client, method, suffix, body, authHeaders(role, contentType));
    }

    private HttpHeaders authHeaders(UserRole role, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        if (role != null) {
            String id = "question-editor";
            mongoTemplate.getCollection("users").replaceOne(new Document("_id", id),
                    new Document("_id", id).append("email", "question@example.test").append("password_hash", "unused")
                            .append("role", role.name()).append("locked", false), new com.mongodb.client.model.ReplaceOptions().upsert(true));
            String session = UUID.randomUUID().toString();
            mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", session).append("account_id", id)
                    .append("expires_at", Date.from(Instant.now().plusSeconds(3600))).append("revoked", false));
            headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + tokens.create(id, session, "access", 900));
        }
        return headers;
    }

    private ResponseEntity<String> exchange(org.springframework.web.client.RestTemplate client, HttpMethod method,
            String suffix, String body, HttpHeaders headers) {
        int separator = suffix.indexOf('?');
        String path = separator < 0 ? suffix : suffix.substring(0, separator);
        String query = separator < 0 ? null : suffix.substring(separator + 1);
        var uri = org.springframework.web.util.UriComponentsBuilder.newInstance().scheme("http").host("localhost")
                .port(port).path(BASE).path(path).query(query).build().encode().toUri();
        try {
            return client.exchange(uri, method, new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }

    private void authenticate(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("question-editor", "question@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private JsonNode data(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return json.readTree(response.getBody()).path("data");
    }

    private void assertIds(JsonNode data, String... expected) {
        var ids = new java.util.ArrayList<String>();
        data.path("items").forEach(item -> ids.add(item.path("questionId").asString()));
        assertThat(ids).containsExactly(expected);
    }

    private void assertError(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(json.readTree(response.getBody()).path("code").asString()).isEqualTo(code);
        assertThat(response.getBody()).doesNotContain("database secret", "provider secret");
    }
}
