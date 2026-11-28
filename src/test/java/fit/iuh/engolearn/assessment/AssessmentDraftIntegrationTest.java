package fit.iuh.engolearn.assessment;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

@Import(AssessmentStorageTestSupport.class)
@EnabledIf("mongoAvailable")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "engolearn.security.jwt.secret=isolated-assessment-draft-integration-tests-key",
        "engolearn.security.cookies.secure=false", "engolearn.messaging.enabled=false",
        "spring.mongodb.username=", "spring.mongodb.password="
})
class AssessmentDraftIntegrationTest {
    private static final String DATABASE = "engolearn_assessment_draft_it";
    private static MongoDBContainer container;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private fit.iuh.engolearn.media.contract.MediaManagement mediaManagement;
    @LocalServerPort private int port;
    @Autowired private AssessmentStorageTestSupport.LocalStorage storage;
    private static final String DISPOSABLE_MONGO = System.getenv("ASSESSMENT_DRAFT_TEST_MONGO_URI");

    static boolean mongoAvailable() {
        return DISPOSABLE_MONGO != null && !DISPOSABLE_MONGO.isBlank() || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void optionalReplicaSet(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", AssessmentDraftIntegrationTest::mongoUri);
        registry.add("spring.mongodb.database", () -> DATABASE);
    }

    private static synchronized String mongoUri() {
        if (DISPOSABLE_MONGO != null && !DISPOSABLE_MONGO.isBlank()) return DISPOSABLE_MONGO;
        if (container == null) {
            container = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble")).withReplicaSet();
            container.start();
        }
        return container.getReplicaSetUrl(DATABASE);
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) container.stop();
    }

    @BeforeEach
    void clearFixtures() throws Exception {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo(DATABASE);
        for (String name : mongoTemplate.getCollectionNames()) {
            mongoTemplate.getCollection(name).deleteMany(new Document());
        }
        fit.iuh.engolearn.microskill.MicroSkillFixtures.seed(mongoTemplate);
        storage.reset();
    }

    private static final String DRAFTS = "/api/v1/assessment/admin/drafts";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> call(String method, String path, String body, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = json.writeValueAsString(Map.of("email", email, "password", "Isolated-draft-123!"));
        assertThat(call("POST", "/api/v1/auth/register", body, null).statusCode()).isEqualTo(201);
        mongoTemplate.getCollection("users").updateOne(new Document("email", email),
                new Document("$set", new Document("role", role)));
        var response = call("POST", "/api/v1/auth/login", body, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream().map(v -> v.split(";", 2)[0])
                .filter(v -> v.startsWith("ACCESS_TOKEN=")).findFirst().orElseThrow();
    }

    @Test void onlyContentStaffCanOpenDrafts() throws Exception {
        assertThat(call("GET", DRAFTS, null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", DRAFTS, null, login("USER")).statusCode()).isEqualTo(403);
        assertThat(call("GET", DRAFTS, null, login("CONTENT_MANAGER")).statusCode()).isEqualTo(200);
        assertThat(call("GET", DRAFTS, null, login("ADMIN")).statusCode()).isEqualTo(200);
        assertThat(call("GET", DRAFTS + "/skill-catalog", null, login("USER")).statusCode()).isEqualTo(403);
        var catalog = call("GET", DRAFTS + "/skill-catalog", null, login("CONTENT_MANAGER"));
        assertThat(catalog.statusCode()).isEqualTo(200);
        assertThat(json.readTree(catalog.body()).path("data").size()).isEqualTo(5);
    }

    @Test void twoEditorsCannotOverwriteEachOtherOrDuplicateADraft() throws Exception {
        String cookie = login("CONTENT_MANAGER");
        String create = "{\"series\":\"F03-M1\",\"name\":\"\"}";
        var first = call("POST", DRAFTS, create, cookie);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(call("POST", DRAFTS, create, cookie).statusCode()).isEqualTo(409);
        String revision = json.readTree(first.body()).at("/data/revision").asString();
        String content = """
                {"revision":"%s","name":"%s","skills":[],"units":[{"kind":"READING_SINGLE",
                "questions":[{"prompt":"","options":[],"correctOption":null}]}]}
                """;
        String path = DRAFTS + "/F03-M1";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var go = new CountDownLatch(1);
            var one = pool.submit(() -> { go.await(); return call("PUT", path, content.formatted(revision, "Một"), cookie); });
            var two = pool.submit(() -> { go.await(); return call("PUT", path, content.formatted(revision, "Hai"), cookie); });
            go.countDown();
            var a = one.get(30, TimeUnit.SECONDS);
            var b = two.get(30, TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 409);
            assertThat(json.readTree((a.statusCode() == 409 ? a : b).body()).path("code").asString()).isEqualTo("STALE_DRAFT");
            var reopened = json.readTree(call("GET", path, null, cookie).body()).path("data");
            var winner = json.readTree((a.statusCode() == 200 ? a : b).body()).path("data");
            assertThat(reopened).isEqualTo(winner);
            assertThat(reopened.at("/units/0/questions/0/correctOption").isNumber()).isFalse();
        }
        assertThat(mongoTemplate.getCollection("assessment_papers").countDocuments()).isZero();
    }

    @Test void editingTheShippedExamPreservesItsSourceAndAudio() throws Exception {
        var sample = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var paper = sample.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        var published = mongoTemplate.getCollection("assessment_papers");
        published.insertOne(paper);
        var before = published.find().first();
        String series = paper.getString("series");
        String cookie = login("ADMIN");
        var created = call("POST", DRAFTS, json.writeValueAsString(Map.of("series", series, "name", "Bản tiếp theo")), cookie);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        var data = json.readTree(created.body()).path("data");
        assertThat(data.path("version").asInt()).isEqualTo(paper.getInteger("version") + 1);
        assertThat(data.path("units").size()).isEqualTo(26);
        var body = json.writeValueAsString(Map.of("revision", data.path("revision"), "name", "Tên mới",
                "units", data.path("units"), "skills", data.path("skills")));
        var saved = call("PUT", DRAFTS + "/" + series, body, cookie);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json.readTree(saved.body()).at("/data/units")).isEqualTo(data.path("units"));
        var persisted = mongoTemplate.getCollection("assessment_paper_drafts").find().first();
        assertThat(persisted.getList("units", Document.class).getFirst().getString("audio_sha256"))
                .isNotBlank().isEqualTo(paper.getList("units", Document.class).getFirst().getString("audio_sha256"));
        assertThat(published.find().first()).isEqualTo(before);
    }

    @Test
    void savingChangesInvalidatesPreviewAndUnsupportedSkillCannotBePublished() throws Exception {
        var fixture = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var source = fixture.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        mongoTemplate.getCollection("assessment_papers").insertOne(source);
        String series = source.getString("series");
        String path = DRAFTS + "/" + series;
        String cookie = login("CONTENT_MANAGER");
        var created = call("POST", DRAFTS, json.writeValueAsString(Map.of("series", series, "name", "Tên mới")), cookie);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        var draft = (ObjectNode) json.readTree(created.body()).path("data");
        String oldRequest = json.writeValueAsString(Map.of("revision", draft.path("revision").asString()));
        assertThat(call("POST", path + "/preview", oldRequest, cookie).statusCode()).isEqualTo(200);
        assertThat(call("POST", path + "/review", oldRequest, cookie).statusCode()).isEqualTo(204);

        var skill = (ObjectNode) draft.path("skills").get(0);
        String sourceCode = skill.path("code").asString();
        skill.put("code", "UNMAPPED");
        for (var unit : draft.path("units")) {
            for (var question : unit.path("questions")) {
                if (sourceCode.equals(question.path("skillCode").asString())) {
                    ((ObjectNode) question).put("skillCode", "UNMAPPED");
                }
            }
        }
        var saved = call("PUT", path, json.writeValueAsString(Map.of(
                "revision", draft.path("revision"), "name", draft.path("name"),
                "units", draft.path("units"), "skills", draft.path("skills"))), cookie);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        String request = json.writeValueAsString(Map.of("revision", json.readTree(saved.body()).at("/data/revision").asString()));
        assertThat(call("POST", path + "/review", oldRequest, cookie).statusCode()).isEqualTo(409);
        var review = call("POST", path + "/review", request, cookie);
        assertThat(review.statusCode()).isEqualTo(409);
        assertThat(json.readTree(review.body()).path("code").asString()).isEqualTo("DRAFT_PREVIEW_REQUIRED");
        var preview = call("POST", path + "/preview", request, cookie);
        assertThat(preview.statusCode()).isEqualTo(422);
        assertThat(preview.body()).contains("UNMAPPED");
        assertThat(call("POST", path + "/publish", request, cookie).statusCode()).isEqualTo(409);
        assertThat(mongoTemplate.getCollection("assessment_papers").countDocuments()).isEqualTo(1);
    }

    @Test
    void aReviewedRevisionPublishesOnlyOnceAndCanBeArchived() throws Exception {
        var fixture = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var source = fixture.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        mongoTemplate.getCollection("assessment_papers").insertOne(source);
        String series = source.getString("series");
        String path = DRAFTS + "/" + series;
        String cookie = login("CONTENT_MANAGER");
        var created = call("POST", DRAFTS, json.writeValueAsString(Map.of("series", series, "name", "Phiên bản mới")), cookie);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String revision = json.readTree(created.body()).at("/data/revision").asString();
        String request = json.writeValueAsString(Map.of("revision", revision));

        assertThat(call("POST", path + "/publish", request, cookie).statusCode()).isEqualTo(409);
        var reviewWithoutPreview = call("POST", path + "/review", request, cookie);
        assertThat(reviewWithoutPreview.statusCode()).isEqualTo(409);
        assertThat(json.readTree(reviewWithoutPreview.body()).path("code").asString()).isEqualTo("DRAFT_PREVIEW_REQUIRED");
        var preview = call("POST", path + "/preview", request, cookie);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        var otherReviewer = call("POST", path + "/review", request, login("CONTENT_MANAGER"));
        assertThat(otherReviewer.statusCode()).isEqualTo(409);
        var audio = call("GET", path + "/preview/audio/0?revision=" + revision, null, cookie);
        assertThat(audio.statusCode()).as(audio.body()).isEqualTo(200);
        assertThat(json.readTree(audio.body()).at("/data/base64").asString()).isNotBlank();
        var reviewed = call("POST", path + "/review", request, cookie);
        assertThat(reviewed.statusCode()).as(reviewed.body()).isEqualTo(204);
        var released = call("POST", path + "/publish", request, cookie);
        assertThat(released.statusCode()).as(released.body()).isEqualTo(200);
        var data = json.readTree(released.body()).path("data");
        var replay = call("POST", path + "/publish", request, cookie);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("data")).isEqualTo(data);
        var papers = mongoTemplate.getCollection("assessment_papers");
        assertThat(papers.countDocuments(new Document("series", series))).isEqualTo(2);
        assertThat(papers.countDocuments(new Document("series", series).append("published", true))).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("assessment_attempts").countDocuments()).isZero();

        String archivePath = "/api/v1/assessment/admin/papers/" + series + "/" + data.path("version").asInt() + "/archive";
        assertThat(call("POST", archivePath, "{}", login("USER")).statusCode()).isEqualTo(403);
        assertThat(call("POST", archivePath, "{}", cookie).statusCode()).isEqualTo(200);
        assertThat(call("POST", archivePath, "{}", cookie).statusCode()).isEqualTo(200);
        assertThat(papers.countDocuments(new Document("series", series).append("published", true))).isZero();
        assertThat(papers.countDocuments(new Document("series", series))).isEqualTo(2);
    }
    private static final String IMPORTS = "/api/v1/assessment/admin/draft-imports";
    private static final String SKILLS = "/api/management/micro-skills";

    private record Part(String name, String filename, String contentType, byte[] bytes) {
        static Part text(String name, String type, String text) {
            return new Part(name, null, type, text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private HttpResponse<String> multipart(String route, String cookie, Part... parts) throws Exception {
        var boundary = "TestBoundary" + UUID.randomUUID();
        var data = new ByteArrayOutputStream();
        for (Part part : parts) {
            String disposition = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + part.name() + "\"";
            if (part.filename() != null) disposition += "; filename=\"" + part.filename() + "\"";
            data.write((disposition + "\r\nContent-Type: " + part.contentType() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            data.write(part.bytes());
            data.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        data.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                .timeout(java.time.Duration.ofSeconds(25))
                .header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void adminCatalogUsesStableCodesAndNeverRewritesPublishedSkills() throws Exception {
        String admin = login("ADMIN");
        String content = login("CONTENT_MANAGER");
        String learner = login("USER");
        assertThat(call("GET", SKILLS, null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", SKILLS, null, learner).statusCode()).isEqualTo(403);
        assertThat(call("GET", SKILLS, null, content).statusCode()).isEqualTo(200);
        assertThat(json.readTree(call("GET", SKILLS, null, admin).body()).at("/data/items").size()).isEqualTo(10);
        var newSkill = """
                {"code":"R99","canonicalName":"New reading skill","displayNameVi":"Chờ mapping",
                 "descriptionVi":"Kỹ năng mới","skill":"READING","status":"INACTIVE","displayOrder":20}
                """;
        assertThat(call("POST", SKILLS, newSkill, learner).statusCode()).isEqualTo(403);
        var created = call("POST", SKILLS, newSkill, admin);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(json.readTree(created.body()).at("/data/status").asString()).isEqualTo("INACTIVE");
        assertThat(call("POST", SKILLS, newSkill, admin).statusCode()).isEqualTo(409);

        var sample = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var paper = sample.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        var papers = mongoTemplate.getCollection("assessment_papers");
        papers.insertOne(paper);
        var before = papers.find().first();
        var renamed = updateGist(admin, "Đọc ý chính mới", "ACTIVE");
        assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
        assertThat(json.readTree(renamed.body()).at("/data/canonicalName").asString()).isEqualTo("Reading for Gist");
        var options = json.readTree(call("GET", DRAFTS + "/skill-catalog", null, content).body()).path("data");
        assertThat(options.toString()).contains("Đọc ý chính mới").doesNotContain("R99");
        assertThat(updateGist(admin, "Đọc ý chính mới", "INACTIVE").statusCode()).isEqualTo(200);
        assertThat(call("GET", DRAFTS + "/skill-catalog", null, content).body()).doesNotContain("R02");
        assertThat(papers.find().first()).isEqualTo(before);
    }

    private HttpResponse<String> updateGist(String cookie, String label, String status) throws Exception {
        String route = SKILLS + "/MS-R-GIST";
        var current = json.readTree(call("GET", route, null, cookie).body()).path("data");
        return call("PUT", route, json.writeValueAsString(Map.of(
                "revision", current.path("revision").asInt(), "displayOrder", current.path("displayOrder").asInt(),
                "descriptionVi", current.path("descriptionVi").asString(), "displayNameVi", label, "status", status)), cookie);
    }

    private URI providerUrl(Document draft, int index) {
        var unit = draft.getList("units", Document.class).get(index);
        var asset = mongoTemplate.getCollection("assessment_audio_assets")
                .find(new Document("_id", unit.getString("audio_sha256"))).first();
        return mediaManagement.resolve(asset.getString("media_id")).url();
    }

    @Test
    void alteredWavIsNotRegisteredAndAnExistingDigestIsCheckedOnRetry() throws Exception {
        String admin = login("ADMIN");
        byte[] wav = Files.readAllBytes(Path.of("tools/mongo-json-importer/samples/assessment-media/sound-check.wav"));
        String digest = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(wav));
        String route = "/api/v1/assessment/admin/media";
        var file = new Part("file", "source.wav", "audio/wav", wav);
        var registered = mongoTemplate.getCollection("assessment_audio_assets");
        var media = mongoTemplate.getCollection("media_assets");

        storage.addBytesOnUpload(34);
        var altered = multipart(route, admin, file);
        assertThat(altered.statusCode()).as(altered.body()).isEqualTo(503);
        assertThat(registered.countDocuments()).isZero();
        assertThat(media.countDocuments()).isZero();
        assertThat(storage.stored()).isZero();

        storage.addBytesOnUpload(0);
        assertThat(multipart(route, admin, file).statusCode()).isEqualTo(200);
        var association = registered.find(new Document("_id", digest)).first();
        assertThat(association).isNotNull();
        String mediaId = association.getString("media_id");
        URI url = storage.urlFor("engolearn/media/" + mediaId);
        storage.replaceObject(url, Arrays.copyOf(wav, wav.length + 34));

        var stale = multipart(route, admin, file);
        assertThat(stale.statusCode()).as(stale.body()).isEqualTo(503);
        assertThat(registered.find(new Document("_id", digest)).first()).isEqualTo(association);
        assertThat(media.countDocuments()).isEqualTo(1);
        assertThat(storage.stored()).isEqualTo(1);

        storage.replaceObject(url, wav);
        assertThat(multipart(route, admin, file).statusCode()).isEqualTo(200);
        assertThat(registered.find(new Document("_id", digest)).first()).isEqualTo(association);
        assertThat(media.countDocuments()).isEqualTo(1);
    }

    @Test
    void workbookPreviewAndMediaRetryCreateExactlyOneReviewedRevision() throws Exception {
        workbook(PublicationCase.RECOVERY);
    }

    @Test void truncatedObjectBeforePublicationKeepsDraftAndPreviousPaper() throws Exception {
        workbook(PublicationCase.TRUNCATE);
    }

    @Test void retiredSkillDuringPublicationKeepsDraftAndPreviousPaper() throws Exception {
        workbook(PublicationCase.RETIRE);
    }

    @Test void revisionEditedDuringPublicationKeepsTheNewDraft() throws Exception {
        workbook(PublicationCase.EDIT_REVISION);
    }

    private enum PublicationCase { RECOVERY, TRUNCATE, RETIRE, EDIT_REVISION }

    private void workbook(PublicationCase publicationCase) throws Exception {
        String admin = login("ADMIN");
        String learner = login("USER");
        assertThat(call("GET", IMPORTS + "/template", null, learner).statusCode()).isEqualTo(403);
        var template = call("GET", IMPORTS + "/template", null, admin);
        assertThat(template.statusCode()).isEqualTo(200);
        assertThat(Base64.getDecoder().decode(json.readTree(template.body()).at("/data/base64").asString()))
                .startsWith((byte) 'P', (byte) 'K');
        var bad = new Part("file", "bad.wav", "audio/wav", "broken".getBytes(StandardCharsets.UTF_8));
        assertThat(multipart(IMPORTS + "/inspect-audio", admin, bad).statusCode()).isEqualTo(422);

        Part workbook;
        try (var input = getClass().getResourceAsStream("/assessment/pfc-workbook-fixture.xlsx")) {
            workbook = new Part("workbook", "valid.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", input.readAllBytes());
        }
        var source = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var paper = source.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        paper.put("_id", "paper:PFC-EXCEL-TEST:1");
        paper.put("series", "PFC-EXCEL-TEST");
        paper.put("version", 1);
        var papers = mongoTemplate.getCollection("assessment_papers");
        papers.insertOne(paper);
        Path dir = Path.of("tools/mongo-json-importer/samples/assessment-media");
        var wavs = new ArrayList<Part>();
        for (int index = 0; index < 12; index++) {
            var filename = paper.getList("units", Document.class).get(index).getString("audio_path");
            wavs.add(new Part("file", "nghe-%02d.wav".formatted(index + 1), "audio/wav", Files.readAllBytes(dir.resolve(filename))));
        }
        wavs.add(new Part("file", "am-thu.wav", "audio/wav", Files.readAllBytes(dir.resolve("sound-check.wav"))));
        var checked = json.createArrayNode();
        for (var wav : wavs) {
            var inspected = multipart(IMPORTS + "/inspect-audio", admin, wav);
            assertThat(inspected.statusCode()).as(inspected.body()).isEqualTo(200);
            checked.add(json.readTree(inspected.body()).path("data"));
        }
        var incomplete = checked.deepCopy();
        incomplete.remove(0);
        var missing = multipart(IMPORTS + "/preview", admin, workbook,
                Part.text("manifest", "application/json", incomplete.toString()));
        assertThat(missing.statusCode()).as(missing.body()).isEqualTo(422);
        assertThat(json.readTree(missing.body()).path("detail").asString()).contains("Phan", "nghe-01.wav");
        var duplicate = checked.deepCopy();
        ((ObjectNode) duplicate.get(1)).put("sha256", duplicate.get(0).path("sha256").asString());
        assertThat(multipart(IMPORTS + "/preview", admin, workbook,
                Part.text("manifest", "application/json", duplicate.toString())).statusCode()).isEqualTo(422);
        var brokenWorkbook = new Part("workbook", "broken.xlsx", workbook.contentType(),
                "not xlsx".getBytes(StandardCharsets.UTF_8));
        assertThat(multipart(IMPORTS + "/preview", admin, brokenWorkbook,
                Part.text("manifest", "application/json", checked.toString())).statusCode()).isEqualTo(422);
        var manifest = Part.text("manifest", "application/json", checked.toString());
        var preview = multipart(IMPORTS + "/preview", admin, workbook, manifest);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        var dryRun = json.readTree(preview.body()).path("data");
        assertThat(dryRun.at("/draft/units").size()).isEqualTo(26);
        assertThat(dryRun.path("audioFiles").size()).isEqualTo(12);
        assertThat(dryRun.path("soundCheckFile").asString()).isEqualTo("am-thu.wav");
        assertThat(storage.stored()).isZero();
        assertThat(mongoTemplate.getCollection("assessment_paper_drafts").countDocuments()).isZero();
        var token = Part.text("token", "text/plain", dryRun.path("token").asString());
        assertThat(multipart(IMPORTS, admin, workbook, manifest,
                Part.text("token", "text/plain", "stale")).statusCode()).isEqualTo(409);
        assertThat(multipart(IMPORTS, admin, workbook, manifest, token).statusCode()).isEqualTo(422);

        storage.failOnceAt(3);
        for (int i = 0; i < 3; i++) {
            var upload = multipart("/api/v1/assessment/admin/media", admin, wavs.get(i));
            assertThat(upload.statusCode()).as(upload.body()).isEqualTo(i == 2 ? 503 : 200);
        }
        assertThat(storage.stored()).isEqualTo(2);
        assertThat(mongoTemplate.getCollection("assessment_paper_drafts").countDocuments()).isZero();
        for (int i = 0; i < wavs.size(); i++) {
            var sha = Part.text("sha256", "text/plain", checked.get(i).path("sha256").asString());
            var upload = multipart("/api/v1/assessment/admin/media", admin, wavs.get(i), sha);
            assertThat(upload.statusCode()).as(upload.body()).isEqualTo(200);
        }
        assertThat(storage.stored()).isEqualTo(13);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> multipart(IMPORTS, admin, workbook, manifest, token));
            var second = pool.submit(() -> multipart(IMPORTS, admin, workbook, manifest, token));
            var a = first.get(30, TimeUnit.SECONDS);
            var b = second.get(30, TimeUnit.SECONDS);
            assertThat(a.statusCode()).as(a.body()).isEqualTo(200);
            assertThat(b.statusCode()).as(b.body()).isEqualTo(200);
            assertThat(json.readTree(a.body()).path("data")).isEqualTo(json.readTree(b.body()).path("data"));
        }
        assertThat(mongoTemplate.getCollection("assessment_paper_drafts").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("assessment_draft_imports").countDocuments()).isEqualTo(1);
        var same = multipart(IMPORTS, admin, workbook, manifest, token);
        String revision = json.readTree(same.body()).at("/data/revision").asString();
        var draft = mongoTemplate.getCollection("assessment_paper_drafts").find().first();
        assertThat(draft.getList("units", Document.class).getFirst().getString("audio_media_id")).isNotBlank();
        assertThat(draft.getList("units", Document.class).getFirst().getString("audio_path")).isNull();
        String path = DRAFTS + "/PFC-EXCEL-TEST";
        String body = json.writeValueAsString(Map.of("revision", revision));
        assertThat(call("POST", path + "/publish", body, admin).statusCode()).isEqualTo(409);
        assertThat(call("POST", path + "/preview", body, admin).statusCode()).isEqualTo(200);
        assertThat(call("POST", path + "/review", body, admin).statusCode()).isEqualTo(204);
        var unchangedDraft = mongoTemplate.getCollection("assessment_paper_drafts").find().first();
        for (int index : List.of(0, 10)) {
            var advertised = call("GET", path + "/preview/audio/" + index + "?revision=" + revision, null, admin);
            assertThat(advertised.statusCode()).isEqualTo(200);
            var url = providerUrl(unchangedDraft, index);
            var request = HttpRequest.newBuilder(url).build();
            assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            storage.setAvailable(url, false);
            assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            for (String action : List.of("/preview", "/publish")) {
                var failed = call("POST", path + action, body, admin);
                assertThat(failed.statusCode()).as(failed.body()).isEqualTo(503);
                assertThat(json.readTree(failed.body()).path("code").asString()).isEqualTo("MEDIA_UNAVAILABLE");
                assertThat(failed.body()).contains("Phần " + (index + 1) + ":");
            }
            assertThat(mongoTemplate.getCollection("assessment_paper_drafts").find().first()).isEqualTo(unchangedDraft);
            assertThat(papers.countDocuments()).isEqualTo(1);
            assertThat(papers.find(new Document("published", true)).first()).isEqualTo(paper);
            storage.setAvailable(url, true);
        }
        if (publicationCase == PublicationCase.TRUNCATE) {
            var advertised = call("GET", path + "/preview/audio/0?revision=" + revision, null, admin);
            var uri = providerUrl(unchangedDraft, 0);
            byte[] original = http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray()).body();
            storage.replaceObject(uri, Arrays.copyOf(original, 12));
            var full = http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
            var range = http.send(HttpRequest.newBuilder(uri).header("Range","bytes=0-11").build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(full.body()).hasSize(12);
            assertThat(range.headers().firstValue("Content-Range")).contains("bytes 0-11/12");
            var rechecked = call("POST", path + "/preview", body, admin);
            var result = call("POST", path + "/publish", body, admin);
            var live = papers.find(new Document("published", true)).first();
            System.out.printf("AUDIT truncated-publish url=%d full=%d/%d range=%d/%s preview=%d publish=%d draftCount=%d publishedVersion=%d previousLive=%d revisionMatches=%s%n",
                advertised.statusCode(),full.statusCode(),full.body().length,range.statusCode(),range.headers().firstValue("Content-Range").orElse("?"),
                rechecked.statusCode(),result.statusCode(),mongoTemplate.getCollection("assessment_paper_drafts").countDocuments(),
                live.getInteger("version"),papers.countDocuments(new Document("version",1).append("published",true)),
                revision.equals(live.getString("published_draft_revision")));
            for (var rejected : List.of(rechecked, result)) {
                assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(503);
                assertThat(json.readTree(rejected.body()).path("code").asString()).isEqualTo("MEDIA_UNAVAILABLE");
                assertThat(rejected.body()).contains("Phần 1:");
            }
            assertThat(mongoTemplate.getCollection("assessment_paper_drafts").find().first()).isEqualTo(unchangedDraft);
            assertThat(papers.countDocuments()).isEqualTo(1);
            assertThat(live).isEqualTo(paper);
            var recovered = storage.recoverUnderSameMediaId(mongoTemplate, uri, original);
            assertThat(http.send(HttpRequest.newBuilder(recovered).build(), HttpResponse.BodyHandlers.ofByteArray()).body())
                    .containsExactly(original);
            assertThat(mongoTemplate.getCollection("assessment_paper_drafts").find().first()).isEqualTo(unchangedDraft);
        }
        if (publicationCase == PublicationCase.RETIRE) {
            assertThat(unchangedDraft.getList("skills",Document.class)).anyMatch(s -> "R02".equals(s.getString("code")));
            var retired = new java.util.concurrent.atomic.AtomicInteger();
            var stillOld = new java.util.concurrent.atomic.AtomicInteger();
            storage.onNextDownload(() -> {
                try {
                    retired.set(updateGist(admin, "Đọc ý chính mới", "INACTIVE").statusCode());
                    stillOld.set(papers.find(new Document("published",true)).first().getInteger("version"));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            var result = call("POST", path + "/publish", body, admin);
            assertThat(retired.get()).isEqualTo(200);
            assertThat(stillOld.get()).isEqualTo(1);
            var live = papers.find(new Document("published",true)).first();
            System.out.printf("AUDIT retire-during-range retire=%d liveAtRetire=%d publish=%d liveAfterPublish=%d includesR02=%s gistStatus=%s draftCount=%d%n",
                retired.get(), stillOld.get(), result.statusCode(), live.getInteger("version"),
                live.getList("skills",Document.class).stream().anyMatch(s -> "R02".equals(s.getString("code"))),
                mongoTemplate.getCollection("micro_skills").find(new Document("_id", "MS-R-GIST")).first().getString("status"),
                mongoTemplate.getCollection("assessment_paper_drafts").countDocuments());
            assertThat(result.statusCode()).isEqualTo(422);
            assertThat(json.readTree(result.body()).path("code").asString()).isEqualTo("MICRO_SKILL_UNAVAILABLE");
            assertThat(live).isEqualTo(paper);
            assertThat(papers.countDocuments()).isEqualTo(1);
            assertThat(mongoTemplate.getCollection("assessment_paper_drafts").find().first()).isEqualTo(unchangedDraft);
            return;
        }
        if (publicationCase == PublicationCase.EDIT_REVISION) {
            var draftView = json.readTree(call("GET", path, null, admin).body()).path("data");
            var nextDraft = json.writeValueAsString(Map.of("revision", revision, "name", "Sửa trong lúc kiểm WAV",
                    "units", draftView.path("units"), "skills", draftView.path("skills")));
            var edit = new CompletableFuture<HttpResponse<String>>();
            storage.onNextDownload(() -> {
                try {
                    edit.complete(call("PUT", path, nextDraft, admin));
                } catch (Exception failure) {
                    edit.completeExceptionally(failure);
                }
            });
            var conflictingPublish = call("POST", path + "/publish", body, admin);
            assertThat(edit.get(2, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(conflictingPublish.statusCode()).isEqualTo(409);
            assertThat(json.readTree(conflictingPublish.body()).path("code").asString()).isEqualTo("STALE_DRAFT");
            var revised = json.readTree(edit.join().body()).path("data").path("revision").asString();
            var savedDraft = mongoTemplate.getCollection("assessment_paper_drafts").find().first();
            assertThat(savedDraft.getString("revision")).isEqualTo(revised).isNotEqualTo(revision);
            assertThat(papers.countDocuments()).isEqualTo(1);
            assertThat(papers.find(new Document("published", true)).first()).isEqualTo(paper);
            System.out.printf("PFC revision-race edit=200 publish=409 oldRevision=%s currentRevision=%s live=1%n",
                    revision, revised);
            return;
        }

        HttpResponse<String> published;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var go = new CountDownLatch(1);
            Callable<HttpResponse<String>> publish = () -> { go.await(); return call("POST", path + "/publish", body, admin); };
            var first = pool.submit(publish);
            var second = pool.submit(publish);
            go.countDown();
            published = first.get(30, TimeUnit.SECONDS);
            var concurrent = second.get(30, TimeUnit.SECONDS);
            assertThat(concurrent.statusCode()).as(concurrent.body()).isEqualTo(200);
            assertThat(json.readTree(concurrent.body()).path("data")).isEqualTo(json.readTree(published.body()).path("data"));
        }
        assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
        assertThat(json.readTree(published.body()).at("/data/version").asInt()).isEqualTo(2);
        var liveAfterPublish = papers.find(new Document("published", true)).first();
        assertThat(updateGist(admin, "Đọc ý chính mới", "INACTIVE").statusCode()).isEqualTo(200);
        assertThat(papers.find(new Document("published", true)).first()).isEqualTo(liveAfterPublish);
        var repeated = call("POST", path + "/publish", body, admin);
        assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(200);
        assertThat(json.readTree(repeated.body()).path("data")).isEqualTo(json.readTree(published.body()).path("data"));
        assertThat(multipart(IMPORTS, admin, workbook, manifest, token).statusCode()).isEqualTo(409);
        assertThat(papers.countDocuments()).isEqualTo(2);
        assertThat(papers.countDocuments(new Document("published", true).append("published_draft_revision", revision)))
                .isEqualTo(1);
        assertThat(mongoTemplate.getCollection("assessment_paper_drafts").countDocuments()).isZero();
        System.out.printf("PFC publish-recovery mode=%s concurrent=200/200 retireAfter=200 replay=200 revision=%s liveVersion=2 papers=2 drafts=0%n",
                publicationCase, revision);
    }
}
