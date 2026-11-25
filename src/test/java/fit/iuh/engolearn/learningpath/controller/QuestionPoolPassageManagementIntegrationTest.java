package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
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
        "spring.servlet.multipart.max-file-size=64KB", "spring.servlet.multipart.max-request-size=1MB"})
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

    private ResponseEntity<String> post(String metadata, byte[] audio, String type, UserRole role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
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
            return restTemplate.exchange("http://localhost:" + port + BASE, HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            return new ResponseEntity<>(exception.getResponseBodyAsString(), exception.getResponseHeaders(), exception.getStatusCode());
        }
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
