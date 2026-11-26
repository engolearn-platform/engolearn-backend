package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
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
    private static final String BASE = "/api/management/learning-paths/questions";
    private static final String MICRO_SKILL = "Reading for Gist";
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private QuestionPoolQuestionManagementService service;
    @MockitoSpyBean private QuestionPoolQuestionRepository questions;
    @MockitoBean private MediaManagement media;

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
        HttpHeaders headers = new HttpHeaders();
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
        int separator = suffix.indexOf('?');
        String path = separator < 0 ? suffix : suffix.substring(0, separator);
        String query = separator < 0 ? null : suffix.substring(separator + 1);
        var uri = org.springframework.web.util.UriComponentsBuilder.newInstance().scheme("http").host("localhost")
                .port(port).path(BASE).path(path).query(query).build().encode().toUri();
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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
