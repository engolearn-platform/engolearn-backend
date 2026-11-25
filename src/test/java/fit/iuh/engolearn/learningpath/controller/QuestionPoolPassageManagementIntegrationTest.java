package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolPassagesRequest;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=64KB", "spring.servlet.multipart.max-request-size=1MB",
        "engolearn.media.protected-url-lifetime=60s"})
class QuestionPoolPassageManagementIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/learning-paths/passages";
    private static final String READING = "{\"skill\":\"READING\",\"level\":\"B1\",\"title\":\"  Library hours  \",\"text\":\"The library opens at nine.\"}";
    private static final String LISTENING = "{\"skill\":\"LISTENING\",\"level\":\"A2\",\"title\":\"Bus timetable\",\"transcript\":\"The bus arrives at ten.\"}";
    private static final byte[] MP3 = "ID3-test-audio-header".getBytes(StandardCharsets.US_ASCII);
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private JwtTokenService tokens;
    @Autowired private QuestionPoolPassageManagementService service;
    @MockitoBean private MediaStorageProvider storage;
    @MockitoSpyBean private QuestionPoolPassageRepository passages;

    @BeforeEach
    void storageProvider() {
        restTemplate.setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        when(storage.upload(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            invocation.getArgument(3, InputStream.class).readAllBytes();
            return MediaStorageMetadataVo.builder().provider(MediaProvider.CLOUDINARY)
                    .publicId(invocation.getArgument(0)).assetId(UUID.randomUUID().toString())
                    .resourceType(MediaResourceType.VIDEO).deliveryType(MediaDeliveryType.AUTHENTICATED)
                    .format("mp3").version(1L).build();
        });
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void createsReadingDraftWithTrustedAuditAndNoMedia(UserRole role) throws Exception {
        var response = post(READING, null, null, role);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = json.readTree(response.getBody()).path("data");
        Document saved = mongoTemplate.getCollection("question_pool_passages")
                .find(new Document("passage_id", data.path("passageId").asString())).first();
        assertThat(saved).isNotNull();
        assertThat(saved.getString("status")).isEqualTo("DRAFT");
        assertThat(saved.getInteger("revision")).isEqualTo(1);
        assertThat(saved.getString("created_by")).isEqualTo("passage-editor");
        assertThat(saved.getString("title")).isEqualTo("Library hours");
        assertThat(saved.getString("level")).isEqualTo("B1");
        assertThat(saved.getDate("created_at")).isNotNull();
        assertThat(saved.getDate("updated_at")).isNotNull();
        assertThat(saved.get("audio_media_id")).isNull();
        verifyNoInteractions(storage);
        assertNoLearnerWrites();
    }

    @Test
    void uploadsListeningThroughRealMediaInspectionAndPersistsProtectedReference() throws Exception {
        var response = post(LISTENING, MP3, "audio/mpeg", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = json.readTree(response.getBody()).path("data");
        String mediaId = data.path("audioMediaId").asString();
        Document saved = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(saved.getString("audio_media_id")).isEqualTo(mediaId);
        assertThat(saved.get("audio")).isNull();
        assertThat(saved.getString("status")).isEqualTo("DRAFT");
        assertThat(saved.getString("transcript")).isEqualTo("The bus arrives at ten.");
        Document media = mongoTemplate.getCollection("media_assets").find().first();
        assertThat(media.get("_id").toString()).isEqualTo(mediaId);
        assertThat(media.getString("access_policy")).isEqualTo("PROTECTED");
        assertThat(media.getString("purpose")).isEqualTo("LEARNING_PATH_PASSAGE_AUDIO");
        verify(storage).upload(anyString(), any(), eq(MediaAccessPolicy.PROTECTED), any());
        assertNoLearnerWrites();
    }

    @Test
    void guestAndLearnerCannotUploadOrCreate() {
        assertError(post(READING, null, null, null), 401, "AUTHENTICATION_REQUIRED");
        assertError(post(READING, null, null, UserRole.USER), 403, "ACCESS_DENIED");
        assertNoPassageOrMedia();
        verifyNoInteractions(storage);
    }

    @Test
    void serviceProxyEnforcesRolesEvenWithoutAnHttpCaller() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var request = new CreateQuestionPoolPassageRequest(QuestionPoolSkill.READING, CefrLevel.B1, "Title", "Text", null);
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.create(request, null, null, null, 0))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.create(request, null, null, null, 0)).isInstanceOf(AccessDeniedException.class);
            authenticate(UserRole.CONTENT_MANAGER);
            assertThat(service.create(request, null, null, null, 0).status().name()).isEqualTo("DRAFT");
            authenticate(UserRole.ADMIN);
            assertThat(service.create(request, null, null, null, 0).status().name()).isEqualTo("DRAFT");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"skill\":\"SPEAKING\",\"level\":\"B1\",\"title\":\"Title\"}",
            "{\"skill\":\"READING\",\"level\":\"INVALID\",\"title\":\"Title\"}",
            "{\"skill\":\"READING\",\"level\":\"B1\",\"title\":\"  \",\"text\":\"Text\"}",
            "{\"skill\":\"READING\",\"level\":\"B1\",\"title\":\"Title\"}",
            "{\"skill\":\"LISTENING\",\"level\":\"B1\",\"title\":\"Title\"}"})
    void rejectsInvalidMetadataWithoutWrites(String metadata) {
        assertThat(post(metadata, null, null, UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertNoPassageOrMedia();
        verifyNoInteractions(storage);
    }

    @Test
    void listeningRequiresAudioAndReadingRejectsAudio() {
        assertError(post(LISTENING, null, null, UserRole.ADMIN), 400, "INVALID_PASSAGE");
        assertError(post(LISTENING, new byte[0], "audio/mpeg", UserRole.ADMIN), 400, "INVALID_PASSAGE");
        assertError(post(READING, MP3, "audio/mpeg", UserRole.ADMIN), 400, "INVALID_PASSAGE");
        assertNoPassageOrMedia();
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnsupportedOrDisguisedAudioBeforeProviderUpload() {
        assertError(post(LISTENING, MP3, "image/png", UserRole.ADMIN), 400, "INVALID_PASSAGE");
        assertError(post(LISTENING, "not an mp3".getBytes(StandardCharsets.US_ASCII), "audio/mpeg", UserRole.ADMIN), 400, "INVALID_PASSAGE");
        assertNoPassageOrMedia();
        verifyNoInteractions(storage);
    }

    @Test
    void mediaFailureDoesNotCreatePassage() {
        doThrow(new MediaStorageUnavailableException("provider error")).when(storage).upload(anyString(), any(), any(), any());
        assertError(post(LISTENING, MP3, "audio/mpeg", UserRole.ADMIN), 503, "MEDIA_STORAGE_UNAVAILABLE");
        assertNoPassageOrMedia();
    }

    @Test
    void passageFailureReturns503AndMayLeaveUploadedMedia() {
        doThrow(new DataAccessResourceFailureException("database error")).when(passages).insert(any());
        assertError(post(LISTENING, MP3, "audio/mpeg", UserRole.ADMIN), 503, "PASSAGE_STORAGE_UNAVAILABLE");
        assertThat(mongoTemplate.getCollection("question_pool_passages").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("media_assets").countDocuments()).isEqualTo(1);
    }

    @Test
    void rejectsOversizedMetadataAndMissingJsonPart() {
        assertThat(post(READING.replace("Library hours", "x".repeat(201)), null, null, UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertThat(post(READING.replace("The library opens at nine.", "x".repeat(50001)), null, null, UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertThat(post(null, null, null, UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertNoPassageOrMedia();
    }

    @Test
    void multipartLimitReturns413WithoutUploadOrPassage() {
        byte[] oversized = new byte[70 * 1024];
        System.arraycopy(MP3, 0, oversized, 0, MP3.length);
        assertError(post(LISTENING, oversized, "audio/mpeg", UserRole.ADMIN), 413, "UPLOAD_TOO_LARGE");
        assertNoPassageOrMedia();
        verifyNoInteractions(storage);
    }

    @Test
    void listDefaultsIncludeAllStatusesAndReturnSummariesWithoutMediaResolution() {
        seedPassage("a", "READING", "B1", "DRAFT", "Reading title", "Passage text", null, 0);
        seedPassage("b", "LISTENING", "A2", "ACTIVE", "Listening title", null, "Transcript", 1);
        seedPassage("c", "READING", "C1", "RETIRED", "Retired title", "Retired text", null, 2);
        var response = list("", UserRole.CONTENT_MANAGER);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).path("data");
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("totalElements").asInt()).isEqualTo(3);
        assertThat(data.path("totalPages").asInt()).isEqualTo(1);
        assertIds(data, "c", "b", "a");
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("title").asString()).isEqualTo("Retired title");
        assertThat(item.path("skill").asString()).isEqualTo("READING");
        assertThat(item.path("level").asString()).isEqualTo("C1");
        assertThat(item.path("status").asString()).isEqualTo("RETIRED");
        assertThat(item.path("revision").asInt()).isEqualTo(1);
        assertThat(item.path("hasAudio").asBoolean()).isFalse();
        assertThat(item.path("createdAt").asString()).isNotBlank();
        assertThat(item.path("updatedAt").asString()).isNotBlank();
        for (JsonNode row : data.path("items")) {
            for (String privateField : new String[]{"text", "transcript", "audio", "audioMediaId", "createdBy"}) {
                assertThat(row.has(privateField)).as(privateField).isFalse();
            }
        }
        verifyNoInteractions(storage);
        assertNoLearnerWrites();
        assertThat(mongoTemplate.getCollection("question_pool_passages").countDocuments()).isEqualTo(3);
    }

    @Test
    void listCombinesFiltersAndSearchesTitleTextAndTranscriptCaseInsensitively() {
        seedPassage("title", "READING", "B1", "DRAFT", "LiBrArY title", "Other text", null, 0);
        seedPassage("text", "READING", "B1", "DRAFT", "Other title", "LIBRARY in text", null, 1);
        seedPassage("transcript", "LISTENING", "B1", "ACTIVE", "Other title", null, "library in transcript", 2);
        seedPassage("wrong-level", "READING", "A2", "DRAFT", "Library", "Text", null, 3);
        seedPassage("wrong-status", "READING", "B1", "RETIRED", "Library", "Text", null, 4);
        seedPassage("unmatched", "READING", "B1", "DRAFT", "Other title", "Other text", null, 5);
        JsonNode all = listData("keyword= library ");
        assertIds(all, "wrong-status", "wrong-level", "transcript", "text", "title");
        JsonNode filtered = listData("keyword=LIBRARY&skill=READING&level=B1&status=DRAFT&size=1&page=0");
        assertIds(filtered, "text");
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(2);
        assertThat(filtered.path("totalPages").asInt()).isEqualTo(2);
        assertIds(listData("keyword=library&skill=READING&level=B1&status=DRAFT&size=1&page=1"), "title");
        assertIds(listData("skill=LISTENING&level=B1&status=ACTIVE"), "transcript");
        verifyNoInteractions(storage);
    }

    @Test
    void searchTreatsRegexSymbolsAsLiteralText() {
        seedPassage("literal", "READING", "B1", "DRAFT", "Literal .* and [", "Text", null, 0);
        seedPassage("ordinary", "READING", "B1", "DRAFT", "Ordinary title", "Text", null, 1);
        assertIds(listData("keyword=.*"), "literal");
        assertIds(listData("keyword=["), "literal");
        assertThat(listData("keyword=   ").path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void pagingUsesIdAsTieBreakerAndKeepsTotalsForOutOfRangePages() {
        for (int index = 0; index < 5; index++) {
            seedPassage("same-" + index, "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        }
        assertIds(listData("size=2&page=0"), "same-4", "same-3");
        assertIds(listData("size=2&page=1"), "same-2", "same-1");
        assertIds(listData("size=2&page=2"), "same-0");
        JsonNode outside = listData("size=2&page=3");
        assertThat(outside.path("items").size()).isZero();
        assertThat(outside.path("totalElements").asInt()).isEqualTo(5);
        assertThat(outside.path("totalPages").asInt()).isEqualTo(3);
        assertThat(listData("size=100&page=2147483647").path("items").size()).isZero();
    }

    @Test
    void emptyListAndNoMatchesReturn200WithZeroTotals() {
        JsonNode empty = listData("");
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("totalElements").asInt()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
        seedPassage("a", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        JsonNode unmatched = listData("keyword=no-match");
        assertThat(unmatched.path("items").size()).isZero();
        assertThat(unmatched.path("totalElements").asInt()).isZero();
        assertThat(unmatched.path("totalPages").asInt()).isZero();
    }

    @Test
    void audioFlagRecognizesNewAndLegacyReferencesWithoutReturningUrls() {
        for (String id : new String[]{"new", "old", "none", "blank", "fallback"}) {
            seedPassage(id, "LISTENING", "B1", "DRAFT", "Title", null, "Transcript", 0);
        }
        setAudio("new", new Document("audio_media_id", "media-uuid"));
        setAudio("old", new Document("audio", new Document("url", "/legacy.mp3")));
        setAudio("blank", new Document("audio_media_id", " ").append("audio", new Document("url", " ")));
        setAudio("fallback", new Document("audio_media_id", "").append("audio", new Document("url", "/fallback.mp3")));
        JsonNode items = listData("").path("items");
        for (JsonNode item : items) {
            boolean expected = java.util.Set.of("new", "old", "fallback").contains(item.path("passageId").asString());
            assertThat(item.path("hasAudio").asBoolean()).as(item.toString()).isEqualTo(expected);
        }
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=abc",
            "skill=SPEAKING", "level=INVALID", "status=PUBLISHED"})
    void listRejectsInvalidFiltersAndPagination(String query) {
        assertError(list(query, UserRole.ADMIN), 400, "INVALID_PASSAGE_REQUEST");
        assertNoPassageOrMedia();
    }

    @Test
    void listRejectsAnOverlongKeyword() {
        assertError(list("keyword=" + "x".repeat(201), UserRole.ADMIN), 400, "INVALID_PASSAGE_REQUEST");
    }

    @Test
    void listRequiresManagerAuthorityAtHttpAndServiceBoundaries() {
        assertError(list("", null), 401, "AUTHENTICATION_REQUIRED");
        assertError(list("", UserRole.USER), 403, "ACCESS_DENIED");
        assertThat(list("", UserRole.CONTENT_MANAGER).getStatusCode().value()).isEqualTo(200);
        assertThat(list("", UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        var request = new ListQuestionPoolPassagesRequest(null, null, null, null, null, null);
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.list(request)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.list(request)).isInstanceOf(AccessDeniedException.class);
            authenticate(UserRole.CONTENT_MANAGER);
            assertThat(service.list(request).totalElements()).isZero();
            authenticate(UserRole.ADMIN);
            assertThat(service.list(request).totalElements()).isZero();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void detailReturnsCompleteReadingContentWithoutChangingPassage(UserRole role) {
        var created = post(READING, null, null, role);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode original = json.readTree(created.getBody()).path("data");
        String id = original.path("passageId").asString();
        Document before = mongoTemplate.getCollection("question_pool_passages").find().first();
        var response = detail(id, role);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode data = json.readTree(response.getBody()).path("data");
        for (String field : new String[]{"passageId", "skill", "level", "title", "text", "transcript",
                "audioMediaId", "status", "revision", "createdBy"}) {
            assertThat(data.path(field)).as(field).isEqualTo(original.path(field));
        }
        assertThat(Instant.parse(data.path("createdAt").asString())).isEqualTo(before.getDate("created_at").toInstant());
        assertThat(Instant.parse(data.path("updatedAt").asString())).isEqualTo(before.getDate("updated_at").toInstant());
        assertThat(data.path("audio").isNull()).isTrue();
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(before);
        verifyNoInteractions(storage);
        assertNoLearnerWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "RETIRED"})
    void detailReadsLegacyListeningAudioAcrossContentStates(String state) {
        seedPassage("legacy", "LISTENING", "B1", state, "Old recording", null, "Complete transcript", 0);
        setAudio("legacy", new Document("audio", new Document("url", "/learning-path-audio/legacy.mp3")
                .append("duration_sec", 47).append("format", "mp3")));
        var response = detail("legacy", UserRole.CONTENT_MANAGER);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).path("data");
        assertThat(data.path("status").asString()).isEqualTo(state);
        assertThat(data.path("transcript").asString()).isEqualTo("Complete transcript");
        assertThat(data.path("audio").path("url").asString()).isEqualTo("/learning-path-audio/legacy.mp3");
        assertThat(data.path("audio").path("durationSec").asInt()).isEqualTo(47);
        assertThat(data.path("audio").path("format").asString()).isEqualTo("mp3");
        assertThat(data.path("audio").path("expiresAt").isNull()).isTrue();
        assertThat(data.path("audio").path("contentType").isNull()).isTrue();
        verifyNoInteractions(storage);
    }

    @Test
    void detailResolvesProtectedMediaAndCanBeRequestedAgainWithoutPersistingUrls() {
        String id = createListeningForDetail();
        setAudio(id, new Document("audio", new Document("url", "/stale-legacy.mp3")));
        Document before = mongoTemplate.getCollection("question_pool_passages").find().first();
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        when(storage.privateDownloadUrl(any(), any())).thenAnswer(invocation ->
                java.net.URI.create("https://media.example.test/preview.mp3?request=" + counter.incrementAndGet()));
        clearInvocations(storage);
        var first = detail(id, UserRole.ADMIN);
        var second = detail(id, UserRole.CONTENT_MANAGER);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode audio = json.readTree(first.getBody()).path("data").path("audio");
        assertThat(audio.path("url").asString()).isEqualTo("https://media.example.test/preview.mp3?request=1");
        assertThat(audio.path("contentType").asString()).isEqualTo("audio/mpeg");
        assertThat(audio.path("durationSec").isNull()).isTrue();
        assertThat(audio.path("format").isNull()).isTrue();
        Instant expiry = Instant.parse(audio.path("expiresAt").asString());
        assertThat(expiry).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(61));
        assertThat(json.readTree(second.getBody()).path("data").path("audio").path("url").asString())
                .isEqualTo("https://media.example.test/preview.mp3?request=2");
        verify(storage, times(2)).privateDownloadUrl(any(), any());
        verify(storage, never()).upload(anyString(), any(), any(), any());
        verify(storage, never()).publicUrl(any());
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(before);
        assertNoLearnerWrites();
    }

    @Test
    void detailMissingPassageIs404AndUsesPassageIdRatherThanMongoId() {
        seedPassage("internal-id", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("_id", "internal-id"),
                new Document("$set", new Document("passage_id", "external-passage-id")));
        assertError(detail("missing", UserRole.ADMIN), 404, "PASSAGE_NOT_FOUND");
        assertError(detail("internal-id", UserRole.ADMIN), 404, "PASSAGE_NOT_FOUND");
        assertThat(detail("external-passage-id", UserRole.ADMIN).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(storage);
    }

    @Test
    void detailMissingMediaIs404WithoutFallingBackToLegacyAudio() {
        seedPassage("broken", "LISTENING", "B1", "DRAFT", "Title", null, "Transcript", 0);
        setAudio("broken", new Document("audio_media_id", UUID.randomUUID().toString())
                .append("audio", new Document("url", "/old.mp3")));
        assertError(detail("broken", UserRole.ADMIN), 404, "MEDIA_NOT_FOUND");
        verifyNoInteractions(storage);
    }

    @Test
    void detailMediaProviderFailureIs503AndDoesNotExposeProviderError() {
        String id = createListeningForDetail();
        doThrow(new MediaStorageUnavailableException("private provider diagnostic"))
                .when(storage).privateDownloadUrl(any(), any());
        var response = detail(id, UserRole.ADMIN);
        assertError(response, 503, "MEDIA_STORAGE_UNAVAILABLE");
        assertThat(response.getBody()).doesNotContain("private provider diagnostic");
    }

    @Test
    void detailWithoutAudioReturnsNullRatherThanInventingARecording() {
        seedPassage("incomplete", "LISTENING", "B1", "DRAFT", "Incomplete legacy draft", null, null, 0);
        var response = detail("incomplete", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).path("data").path("audio").isNull()).isTrue();
        verifyNoInteractions(storage);
    }

    @Test
    void detailAuthorizesBeforeLookupAndMediaResolutionAtHttpAndServiceBoundaries() {
        String id = createListeningForDetail();
        clearInvocations(storage);
        assertError(detail(id, null), 401, "AUTHENTICATION_REQUIRED");
        assertError(detail(id, UserRole.USER), 403, "ACCESS_DENIED");
        assertError(detail("missing", UserRole.USER), 403, "ACCESS_DENIED");
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.detail(id)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.detail(id)).isInstanceOf(AccessDeniedException.class);
            authenticate(UserRole.CONTENT_MANAGER);
            assertThatThrownBy(() -> service.detail("missing")).isInstanceOf(
                    fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException.class);
            authenticate(UserRole.ADMIN);
            assertThatThrownBy(() -> service.detail("missing")).isInstanceOf(
                    fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
        verifyNoInteractions(storage);
    }

    @Test
    void detailRejectsBlankAndOverlongIdentifiers() {
        assertError(detail(" ", UserRole.ADMIN), 400, "INVALID_PASSAGE_REQUEST");
        assertError(detail("x".repeat(101), UserRole.ADMIN), 400, "INVALID_PASSAGE_REQUEST");
    }

    @Test
    void detailDatabaseFailureIs503() {
        doThrow(new DataAccessResourceFailureException("internal database diagnostic"))
                .when(passages).findByPassageId(anyString());
        var response = detail("any-id", UserRole.ADMIN);
        assertError(response, 503, "PASSAGE_STORAGE_UNAVAILABLE");
        assertThat(response.getBody()).doesNotContain("internal database diagnostic");
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONTENT_MANAGER", "ADMIN"})
    void patchReadingChangesOnlyProvidedFieldsAndPreservesAuditAndUnknownData(UserRole role) {
        seedPassage("editable", "READING", "B1", "DRAFT", "Original title", "Original text", null, 0);
        setAudio("editable", new Document("editor_notes", "Keep this unmapped field"));
        var before = mongoTemplate.getCollection("question_pool_passages").find().first();
        var result = patch("editable", "{\"revision\":1,\"title\":\" New title \"}", null, null, role);
        assertThat(result.getStatusCode().value()).as(result.getBody()).isEqualTo(200);
        var data = json.readTree(result.getBody()).path("data");
        assertThat(data.path("title").asString()).isEqualTo("New title");
        assertThat(data.path("text").asString()).isEqualTo("Original text");
        assertThat(data.path("revision").asInt()).isEqualTo(2);
        var after = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(after.get("_id")).isEqualTo(before.get("_id"));
        assertThat(after.getDate("created_at")).isEqualTo(before.getDate("created_at"));
        assertThat(after.getString("created_by")).isEqualTo("another-editor");
        assertThat(after.getString("updated_by")).isEqualTo("passage-editor");
        assertThat(after.getString("editor_notes")).isEqualTo("Keep this unmapped field");
        assertThat(after.getDate("updated_at")).isAfter(before.getDate("updated_at"));
        verifyNoInteractions(storage);
        assertNoLearnerWrites();
    }

    @Test
    void patchListeningKeepsNewAndLegacyAudioWithoutUploadingOrResolving() {
        String id = createListeningForDetail();
        String mediaId = mongoTemplate.getCollection("question_pool_passages").find().first().getString("audio_media_id");
        clearInvocations(storage);
        assertThat(patch(id, "{\"revision\":1,\"transcript\":\"Updated transcript\"}", null, null, UserRole.ADMIN)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first().getString("audio_media_id"))
                .isEqualTo(mediaId);
        seedPassage("legacy-edit", "LISTENING", "B1", "DRAFT", "Title", null, "Transcript", 0);
        Document legacy = new Document("url", "/old.mp3").append("duration_sec", 47)
                .append("format", "mp3").append("extra_metadata", "preserve");
        setAudio("legacy-edit", new Document("audio", legacy));
        assertThat(patch("legacy-edit", "{\"revision\":1,\"title\":\"New title\"}", null, null, UserRole.ADMIN)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("question_pool_passages").find(new Document("passage_id", "legacy-edit"))
                .first().get("audio", Document.class)).isEqualTo(legacy);
        verifyNoInteractions(storage);
    }

    @Test
    void patchCanReplaceAudioAndKeepsOldMediaAsset() {
        String id = createListeningForDetail();
        String oldMediaId = mongoTemplate.getCollection("question_pool_passages").find().first().getString("audio_media_id");
        setAudio(id, new Document("audio", new Document("url", "/outdated.mp3")));
        var response = patch(id, "{\"revision\":1}", MP3, "audio/mpeg", UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        String newMediaId = json.readTree(response.getBody()).path("data").path("audioMediaId").asString();
        assertThat(newMediaId).isNotEqualTo(oldMediaId).isNotBlank();
        var saved = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(saved.getString("audio_media_id")).isEqualTo(newMediaId);
        assertThat(saved.containsKey("audio")).isFalse();
        assertThat(mongoTemplate.getCollection("media_assets").countDocuments()).isEqualTo(2);
        verify(storage, never()).destroy(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RETIRED"})
    void patchRejectsNonDraftBeforeUpload(String state) {
        seedPassage("immutable", "LISTENING", "B1", state, "Title", null, "Transcript", 0);
        assertError(patch("immutable", "{\"revision\":1,\"title\":\"New\"}", MP3, "audio/mpeg", UserRole.ADMIN),
                409, "PASSAGE_UPDATE_CONFLICT");
        verifyNoInteractions(storage);
    }

    @Test
    void patchMissingAndStaleRevisionDoNotUpload() {
        seedPassage("editable", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        assertError(patch("missing", "{\"revision\":1,\"title\":\"New\"}", MP3, "audio/mpeg", UserRole.ADMIN), 404, "PASSAGE_NOT_FOUND");
        assertError(patch("editable", "{\"revision\":2,\"title\":\"New\"}", null, null, UserRole.ADMIN), 409, "PASSAGE_UPDATE_CONFLICT");
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "RETIRED"})
    void patchRejectsClassificationChangesThatMismatchReferencingQuestions(String questionState) {
        seedPassage("referenced", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        mongoTemplate.getCollection("question_pool").insertOne(new Document("question_id", "ref")
                .append("passage_id", "referenced").append("skill", "READING").append("level", "B1").append("status", questionState));
        assertError(patch("referenced", "{\"revision\":1,\"level\":\"A2\"}", null, null, UserRole.ADMIN), 409, "PASSAGE_UPDATE_CONFLICT");
        assertError(patch("referenced", "{\"revision\":1,\"skill\":\"LISTENING\",\"transcript\":\"Transcript\"}",
                MP3, "audio/mpeg", UserRole.ADMIN), 409, "PASSAGE_UPDATE_CONFLICT");
        assertThat(patch("referenced", "{\"revision\":1,\"title\":\"New title\"}", null, null, UserRole.ADMIN)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("question_pool").find().first().getString("level")).isEqualTo("B1");
        verifyNoInteractions(storage);
    }

    @Test
    void patchUnreferencedPassageCanChangeSkillAndLevelWithValidContent() {
        seedPassage("switch", "READING", "B1", "DRAFT", "Title", "Reading text", null, 0);
        var listening = patch("switch", "{\"revision\":1,\"skill\":\"LISTENING\",\"level\":\"A2\",\"transcript\":\"Transcript\"}",
                MP3, "audio/mpeg", UserRole.ADMIN);
        assertThat(listening.getStatusCode().value()).as(listening.getBody()).isEqualTo(200);
        assertThat(json.readTree(listening.getBody()).path("data").path("text").isNull()).isTrue();
        var reading = patch("switch", "{\"revision\":2,\"skill\":\"READING\",\"text\":\"New reading text\"}", null, null, UserRole.ADMIN);
        assertThat(reading.getStatusCode().value()).as(reading.getBody()).isEqualTo(200);
        var saved = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(saved.containsKey("transcript")).isFalse();
        assertThat(saved.containsKey("audio_media_id")).isFalse();
        assertThat(saved.containsKey("audio")).isFalse();
        assertThat(saved.getString("level")).isEqualTo("A2");
        assertThat(saved.getInteger("revision")).isEqualTo(3);
        assertThat(mongoTemplate.getCollection("media_assets").countDocuments()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"revision\":0,\"title\":\"New\"}", "{\"revision\":1}",
            "{\"revision\":1,\"title\":\"  \"}", "{\"revision\":1,\"text\":\" \"}",
            "{\"revision\":1,\"skill\":\"SPEAKING\"}", "{\"revision\":1,\"level\":\"INVALID\"}",
            "{\"revision\":1,\"transcript\":\"Wrong for Reading\"}"})
    void patchRejectsInvalidRequestsWithoutChangingDraft(String body) {
        seedPassage("editable", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        assertThat(patch("editable", body, null, null, UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first().getInteger("revision")).isEqualTo(1);
        verifyNoInteractions(storage);
    }

    @Test
    void patchUploadFailureLeavesOriginalReferenceAndRevision() {
        String id = createListeningForDetail();
        var before = mongoTemplate.getCollection("question_pool_passages").find().first();
        doThrow(new MediaStorageUnavailableException("provider failed")).when(storage).upload(anyString(), any(), any(), any());
        assertError(patch(id, "{\"revision\":1}", MP3, "audio/mpeg", UserRole.ADMIN), 503, "MEDIA_STORAGE_UNAVAILABLE");
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first()).isEqualTo(before);
    }

    @Test
    void patchConcurrentEditorsCannotOverwriteTheSameRevision() throws Exception {
        seedPassage("concurrent", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        doAnswer(invocation -> {
            barrier.await(15, java.util.concurrent.TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passages).updateDraft(any(), eq(1), eq(false));
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> patch("concurrent",
                "{\"revision\":1,\"title\":\"First\"}", null, null, UserRole.ADMIN));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> patch("concurrent",
                "{\"revision\":1,\"title\":\"Second\"}", null, null, UserRole.ADMIN));
        assertThat(java.util.List.of(first.get(25, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value(),
                second.get(25, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        assertThat(mongoTemplate.getCollection("question_pool_passages").find().first().getInteger("revision")).isEqualTo(2);
    }

    @Test
    void patchPublicationRaceRejectsWriteAndMayLeaveNewMediaUnreferenced() {
        String id = createListeningForDetail();
        String oldMediaId = mongoTemplate.getCollection("question_pool_passages").find().first().getString("audio_media_id");
        doAnswer(invocation -> {
            mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("passage_id", id),
                    new Document("$set", new Document("status", "ACTIVE")));
            return invocation.callRealMethod();
        }).when(passages).updateDraft(any(), eq(1), eq(true));
        assertError(patch(id, "{\"revision\":1}", MP3, "audio/mpeg", UserRole.ADMIN), 409, "PASSAGE_UPDATE_CONFLICT");
        var saved = mongoTemplate.getCollection("question_pool_passages").find().first();
        assertThat(saved.getString("status")).isEqualTo("ACTIVE");
        assertThat(saved.getString("audio_media_id")).isEqualTo(oldMediaId);
        assertThat(saved.getInteger("revision")).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("media_assets").countDocuments()).isEqualTo(2);
    }

    @Test
    void patchRequiresManagerRoleAtHttpAndServiceBoundaries() {
        seedPassage("editable", "READING", "B1", "DRAFT", "Title", "Text", null, 0);
        assertError(patch("editable", "{\"revision\":1,\"title\":\"New\"}", null, null, null), 401, "AUTHENTICATION_REQUIRED");
        assertError(patch("editable", "{\"revision\":1,\"title\":\"New\"}", null, null, UserRole.USER), 403, "ACCESS_DENIED");
        var request = new UpdateQuestionPoolPassageRequest(1, null, null, "New", null, null);
        try {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.update("editable", request, null, null, null, 0))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            authenticate(UserRole.USER);
            assertThatThrownBy(() -> service.update("editable", request, null, null, null, 0)).isInstanceOf(AccessDeniedException.class);
            authenticate(UserRole.CONTENT_MANAGER);
            assertThat(service.update("editable", request, null, null, null, 0).revision()).isEqualTo(2);
            authenticate(UserRole.ADMIN);
            assertThat(service.update("editable", new UpdateQuestionPoolPassageRequest(2, null, null, "Again", null, null),
                    null, null, null, 0).revision()).isEqualTo(3);
        } finally {
            SecurityContextHolder.clearContext();
        }
        verifyNoInteractions(storage);
    }

    @Test
    void patchOversizedUploadReturns413() {
        seedPassage("editable", "LISTENING", "B1", "DRAFT", "Title", null, "Transcript", 0);
        byte[] oversized = new byte[70 * 1024];
        assertError(patch("editable", "{\"revision\":1}", oversized, "audio/mpeg", UserRole.ADMIN), 413, "UPLOAD_TOO_LARGE");
        verifyNoInteractions(storage);
    }

    private ResponseEntity<String> patch(String id, String metadata, byte[] audio, String type, UserRole role) {
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString("http://localhost:" + port + BASE)
                .path("/{passageId}").encode().buildAndExpand(id).toUri();
        return multipart(HttpMethod.PATCH, uri, metadata, audio, type, role);
    }

    private String createListeningForDetail() {
        var created = post(LISTENING, MP3, "audio/mpeg", UserRole.ADMIN);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        return json.readTree(created.getBody()).path("data").path("passageId").asString();
    }

    private ResponseEntity<String> detail(String id, UserRole role) {
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:" + port + BASE).path("/{passageId}").encode().buildAndExpand(id).toUri();
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders(role)), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }

    private void seedPassage(String id, String skill, String level, String status,
            String title, String text, String transcript, int minute) {
        Instant created = Instant.parse("2026-11-25T00:00:00Z");
        mongoTemplate.getCollection("question_pool_passages").insertOne(new Document("_id", id)
                .append("passage_id", id).append("skill", skill).append("level", level)
                .append("status", status).append("title", title).append("text", text)
                .append("transcript", transcript).append("revision", 1).append("created_by", "another-editor")
                .append("created_at", Date.from(created)).append("updated_at", Date.from(created.plusSeconds(minute * 60L))));
    }

    private void setAudio(String id, Document fields) {
        mongoTemplate.getCollection("question_pool_passages").updateOne(new Document("_id", id), new Document("$set", fields));
    }

    private JsonNode listData(String query) {
        var response = list(query, UserRole.ADMIN);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return json.readTree(response.getBody()).path("data");
    }

    private void assertIds(JsonNode data, String... ids) {
        java.util.List<String> actual = new java.util.ArrayList<>();
        for (JsonNode item : data.path("items")) actual.add(item.path("passageId").asString());
        assertThat(actual).containsExactly(ids);
    }

    private ResponseEntity<String> list(String query, UserRole role) {
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:" + port + BASE).query(query).build().encode().toUri();
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders(role)), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }

    private ResponseEntity<String> post(String metadata, byte[] audio, String type, UserRole role) {
        return multipart(HttpMethod.POST, java.net.URI.create("http://localhost:" + port + BASE), metadata, audio, type, role);
    }

    private ResponseEntity<String> multipart(HttpMethod method, java.net.URI uri, String metadata,
            byte[] audio, String type, UserRole role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.addAll(authHeaders(role));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        if (metadata != null) {
            HttpHeaders part = new HttpHeaders();
            part.setContentType(MediaType.APPLICATION_JSON);
            parts.add("passage", new HttpEntity<>(metadata, part));
        }
        if (audio != null) {
            HttpHeaders part = new HttpHeaders();
            part.setContentType(MediaType.parseMediaType(type));
            parts.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
                @Override public String getFilename() { return "sample.mp3"; }
            }, part));
        }
        try {
            return restTemplate.exchange(uri, method, new HttpEntity<>(parts, headers), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
    }

    private HttpHeaders authHeaders(UserRole role) {
        HttpHeaders headers = new HttpHeaders();
        if (role != null) {
            String id = "passage-editor";
            mongoTemplate.getCollection("users").replaceOne(new Document("_id", id),
                    new Document("_id", id).append("email", "passage@example.test").append("password_hash", "unused")
                            .append("role", role.name()).append("locked", false), new com.mongodb.client.model.ReplaceOptions().upsert(true));
            String session = UUID.randomUUID().toString();
            mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", session)
                    .append("account_id", id).append("expires_at", Date.from(Instant.now().plusSeconds(3600))).append("revoked", false));
            headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + tokens.create(id, session, "access", 900));
        }
        return headers;
    }

    private void authenticate(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("passage-editor", "passage@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void assertError(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(json.readTree(response.getBody()).path("code").asString()).as(response.getBody()).isEqualTo(code);
    }

    private void assertNoPassageOrMedia() {
        assertThat(mongoTemplate.getCollection("question_pool_passages").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("media_assets").countDocuments()).isZero();
    }

    private void assertNoLearnerWrites() {
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
    }
}
