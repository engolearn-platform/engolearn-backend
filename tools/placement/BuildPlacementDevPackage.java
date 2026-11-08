import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.service.PlacementTestSetSnapshotFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.bson.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import tools.jackson.databind.ObjectMapper;

public class BuildPlacementDevPackage {
    private static final Path BACKEND = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO = BACKEND.getParent();
    private static final Path SEED = BACKEND.resolve("tools/placement/data/seed");
    private static final Path OUTPUT = BACKEND.resolve("tools/placement/data/dev-v3");
    private static final Path SAMPLES = BACKEND.resolve("tools/mongo-json-importer/samples");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path MEDIA = SAMPLES.resolve("placement-media");
    private static final String SET_ID = "PLT-V3-DEV-001-V2";

    public static void main(String[] args) throws Exception {
        boolean check = false;
        boolean verifyAuthoring = false;
        String audioBase = "/api/v1/placement/media";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--check")) check = true;
            else if (args[i].equals("--verify-authoring-source")) verifyAuthoring = true;
            else if (args[i].equals("--audio-base-url") && i + 1 < args.length) audioBase = args[++i];
            else throw new IllegalArgumentException("Unknown/incomplete argument: " + args[i]);
        }
        URI uri = URI.create(audioBase);
        if (uri.isAbsolute()) {
            require(Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || uri.getPath().equals("/")), "Audio base must be an HTTP(S) origin");
            audioBase = uri.getScheme() + "://" + uri.getRawAuthority();
        } else {
            require(audioBase.matches("/[A-Za-z0-9/_-]+") && !audioBase.startsWith("//"), "Audio base must be a local absolute path");
            audioBase = audioBase.replaceAll("/+$", "");
        }

        Map<String,Object> seedManifest = readObject(SEED.resolve("placement_seed_manifest.json"));
        if (verifyAuthoring) verifyFileInfos(seedManifest.get("source"));
        verifyFileInfos(seedManifest.get("output"));
        Path questionFile = SEED.resolve("placement_questions.mongo.json");
        List<Map<String,Object>> raw = readArray(questionFile);
        List<Map<String,Object>> audio = readArray(SEED.resolve("placement_audio_manifest.json"));
        readObject(SEED.resolve("placement_questions.mongo.schema.json"));
        require(raw.size() == 320 && audio.size() == 96, "Expected 320 questions and 96 WAV assets");
        require(raw.stream().filter(q -> "READING".equals(q.get("skill"))).count() == 224, "224 Reading");
        require(raw.stream().filter(q -> "LISTENING".equals(q.get("skill"))).count() == 96, "96 Listening");
        require(raw.stream().allMatch(q -> "DRAFT".equals(q.get("status")) && q.containsKey("audio_url")
                && q.get("audio_url") == null), "Canonical status/audio_url unchanged");

        var conversions = MongoCustomConversions.create(config -> {});
        var mapping = new MongoMappingContext();
        mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mapping.setInitialEntitySet(Set.of(PlacementQuestion.class, PlacementTestSet.class));
        mapping.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mapping);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        Map<String,PlacementQuestion> bank = new LinkedHashMap<>();
        for (Map<String,Object> q : raw) {
            require(q.get("_id") instanceof String, "String question ID");
            var mapped = converter.read(PlacementQuestion.class, new Document(q));
            require(bank.put(mapped.getId(), mapped) == null, "Duplicate question ID");
            require(Objects.equals(mapped.getCorrectOptionId(), q.get("correct_option_id"))
                    && Objects.equals(mapped.getPassage(), q.get("passage")), "Question mapping changed");
            require(mapped.getOptions().size() == 4 && mapped.getOptions().stream()
                    .anyMatch(o -> o.getId().equals(mapped.getCorrectOptionId())), "Correct answer belongs to options");
        }

        Map<String,Map<String,Object>> audioByQuestion = new LinkedHashMap<>();
        List<Map<String,Object>> resolvedAudio = new ArrayList<>();
        long audioBytes = 0;
        for (Map<String,Object> asset : audio) {
            String id = (String) asset.get("question_id");
            require(audioByQuestion.put(id, asset) == null, "Duplicate audio question reference");
            Path source = MEDIA.resolve(asset.get("sha256") + ".wav");
            if (verifyAuthoring) require(sha(REPO.resolve((String) asset.get("source_path"))).equals(asset.get("sha256")), "Authoring WAV checksum");
            require(sha(source).equals(asset.get("sha256")) && Files.size(source) == ((Number) asset.get("bytes")).longValue(), "WAV hash/size mismatch: " + id);
            audioBytes += Files.size(source);
            PlacementQuestion question = bank.get(id);
            require(question != null && question.getSkill() == PlacementSkill.LISTENING, "Audio reference outside bank");
            require(question.getProvenance().getAudioSha256().equals(asset.get("sha256"))
                    && question.getProvenance().getAudioDurationSeconds() == ((Number) asset.get("duration_seconds")).doubleValue(), "Audio metadata mismatch");
            resolvedAudio.add(object("question_id", id, "source_path", BACKEND.relativize(source).toString().replace('\\', '/'),
                    "authoring_source_path", asset.get("source_path"),
                    "sha256", asset.get("sha256"), "bytes", asset.get("bytes"),
                    "duration_seconds", asset.get("duration_seconds"),
                    "url", mediaUrl(audioBase, (String) asset.get("sha256"))));
        }
        require(audioBytes == 48428354, "Canonical WAV total bytes");

        List<String> ids = selectionIds();
        require(new HashSet<>(ids).size() == 20, "20 unique selection IDs");
        List<PlacementQuestion> selected = new ArrayList<>();
        List<Map<String,Object>> selections = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            var question = bank.get(ids.get(i));
            require(question != null, "Selection missing from canonical bank");
            if (question.getSkill() == PlacementSkill.LISTENING) {
                var asset = audioByQuestion.get(question.getId());
                question.setAudioUrl(mediaUrl(audioBase, (String) asset.get("sha256")));
            }
            selected.add(question);
            selections.add(object("order_index", i + 1, "question_id", question.getId(),
                    "skill", question.getSkill().name(), "level", question.getLevel().name(),
                    "semantic_family_id", question.getProvenance().getSemanticFamilyId()));
        }
        var snapshot = PlacementTestSetSnapshotFactory.assemble(SET_ID, "PLT-V3-DEV-001",
                "Placement V3 DEV portable", 2, PlacementTestSetStatus.DRAFT, selected);
        Document snapshotDocument = new Document();
        converter.write(snapshot, snapshotDocument);
        Document roundTrip = new Document();
        converter.write(converter.read(PlacementTestSet.class, snapshotDocument), roundTrip);
        require(snapshotDocument.equals(roundTrip), "Snapshot Mongo round trip");

        String sourceSha = sha(questionFile);
        var questionEnvelope = envelope("engolearn-placement-v3-questions", "Canonical M1 questions; unchanged content and order", sourceSha,
                "placement_questions", raw);
        var snapshotEnvelope = envelope("engolearn-placement-v3-test-sets", "One DEV-only DRAFT snapshot; not published", sourceSha,
                "placement_test_sets", List.of(snapshotDocument));
        var selectionManifest = object("metadata", object("name", "engolearn-placement-v3-selection", "version", 1,
                "source_sha256", sourceSha, "test_set_id", SET_ID), "blueprint", object(
                "A1", object("reading", 4, "listening", 1), "A2", object("reading", 4, "listening", 1),
                "B1", object("reading", 3, "listening", 2), "B2", object("reading", 3, "listening", 2)),
                "questions", selections);
        var audioManifest = object("metadata", object("name", "engolearn-placement-v3-dev-audio", "version", 1,
                "audio_base_url", audioBase, "playback_verified", false), "assets", resolvedAudio);

        LinkedHashMap<String,String> artifacts = new LinkedHashMap<>();
        artifacts.put("placement-v3-questions.importer.json", text(questionEnvelope));
        artifacts.put("placement-v3-test-sets-v2.importer.json", text(snapshotEnvelope));
        artifacts.put("selection-manifest.json", text(selectionManifest));
        artifacts.put("audio-url-manifest.json", text(audioManifest));
        validateExports(artifacts, readArray(questionFile), snapshotDocument, selected, converter);

        Map<String,Object> outputInfos = new LinkedHashMap<>();
        artifacts.forEach((name, content) -> outputInfos.put(name, object("bytes", content.getBytes(StandardCharsets.UTF_8).length,
                "sha256", sha(content.getBytes(StandardCharsets.UTF_8)))));
        List<Map<String,Object>> sources = new ArrayList<>();
        for (Path file : List.of(questionFile, SEED.resolve("placement_questions.mongo.schema.json"),
                SEED.resolve("placement_audio_manifest.json"), SEED.resolve("placement_seed_manifest.json"),
                BACKEND.resolve("tools/mongo-json-importer/mongodb-json-importer-data-format-guide.md"),
                BACKEND.resolve("tools/mongo-json-importer/README.md"), BACKEND.resolve("tools/mongo-json-importer/import.ps1"),
                BACKEND.resolve("tools/mongo-json-importer/importer.mongosh.js"),
                BACKEND.resolve("src/main/java/fit/iuh/engolearn/placement/service/PlacementTestSetSnapshotFactory.java"))) {
            sources.add(object("path", ("engolearn-backend/" + BACKEND.relativize(file).toString()).replace('\\', '/'), "sha256", sha(file)));
        }
        artifacts.put("package-manifest.json", text(object("name", "engolearn-placement-v3-dev-package", "version", 1,
                "audio_base_url", audioBase, "sources", sources, "outputs", outputInfos,
                "counts", object("questions", 320, "reading", 224, "listening", 96, "draft_questions", 320,
                        "wav_assets", 96, "wav_bytes", audioBytes, "test_sets", 1, "snapshot_items", 20),
                "validation", object("source_content_and_order", "PASS", "mongo_converter_round_trip", "PASS",
                        "snapshot_factory", "PASS", "wav_checksums", "PASS", "json_bson_parser", "PASS",
                        "importer_database_dry_run", "NOT_RUN — target not approved", "database_writes", 0),
                "audit_fields", "M1 has no created_at/updated_at; kept absent. New snapshot has no fabricated timestamps.")));

        if (check) {
            for (var artifact : artifacts.entrySet()) {
                Path file = artifactPath(artifact.getKey());
                require(Files.isRegularFile(file) && Files.readString(file).equals(artifact.getValue()), "Stale/missing generated artifact: " + file);
            }
        } else {
            Files.createDirectories(OUTPUT);
            Files.createDirectories(SAMPLES);
            for (var artifact : artifacts.entrySet()) Files.writeString(artifactPath(artifact.getKey()), artifact.getValue(), StandardCharsets.UTF_8);
        }
        require(sha(questionFile).equals(sourceSha), "Canonical M1 was changed during generation");
        System.out.println("Placement DEV package " + (check ? "CHECK" : "BUILD") + ": PASS");
        System.out.println("Questions=320; Reading=224; Listening=96; String IDs=320; DRAFT=320; WAV=96; Test Sets=1; Items=20; Listening URLs=6");
        System.out.println("MappingMongoConverter + actual snapshot factory + BSON parsing: PASS; B2 families unique");
        System.out.println("Audio base=" + audioBase + "; media server NOT started; playback NOT verified");
        outputInfos.forEach((name, info) -> System.out.println(name + " " + info));
        System.out.println("Database writes=0; importer database DryRun: NOT_RUN — target not approved");
    }

    private static String mediaUrl(String base, String sha256) {
        return base + (base.startsWith("/") ? "/" : "/audio/") + sha256 + ".wav";
    }

    private static Path artifactPath(String name) {
        return (name.endsWith(".importer.json") ? SAMPLES : OUTPUT).resolve(name);
    }

    private static void validateExports(Map<String,String> artifacts, List<Map<String,Object>> raw, Document snapshot,
                                        List<PlacementQuestion> selected, MappingMongoConverter converter) {
        Map<String,Object> questions = parseObject(artifacts.get("placement-v3-questions.importer.json"));
        var exported = (List<?>) ((Map<?,?>) questions.get("collections")).get("placement_questions");
        require(raw.equals(exported), "Source/output values or document order changed");
        require(JSON.writeValueAsString(raw).equals(JSON.writeValueAsString(exported)), "Source/output field order changed");
        for (Object q : exported) {
            var bson = BsonDocument.parse(JSON.writeValueAsString(q));
            require(bson.get("_id").isString(), "BSON question ID must remain String");
        }
        var parsedSet = BsonDocument.parse(artifacts.get("placement-v3-test-sets-v2.importer.json"))
                .getDocument("collections").getArray("placement_test_sets").get(0).asDocument();
        require(parsedSet.get("_id").isString() && parsedSet.get("version").isInt32(), "Snapshot ID/version BSON types");
        var items = parsedSet.getArray("items");
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i).asDocument();
            var q = selected.get(i);
            require(item.getString("id").getValue().equals(q.getId()) && item.getInt32("order_index").getValue() == i + 1,
                    "Mongo snapshot identity/order mapping");
            require(!item.containsKey("order") && item.getString("correct_option_id").getValue().equals(q.getCorrectOptionId()), "Mongo field names");
            if (i >= 14) require(item.getDocument("audio").get("duration_seconds").isDouble()
                    && item.getDocument("audio").getString("url").getValue().equals(q.getAudioUrl()), "Listening BSON duration/URL");
        }
        var restored = converter.read(PlacementTestSet.class, Document.parse(parsedSet.toJson()));
        Document roundTrip = new Document();
        converter.write(restored, roundTrip);
        require(roundTrip.equals(snapshot), "Exported snapshot lost fields");
    }

    private static List<String> selectionIds() {
        var ids = new ArrayList<String>();
        for (String level : List.of("A1", "A2", "B1", "B2")) {
            int n = level.equals("A1") || level.equals("A2") ? 4 : 3;
            for (int i = 1; i <= n; i++) ids.add("PLT-Q-" + level + "-R-" + String.format(Locale.ROOT, "%04d", i) + "-V3");
        }
        ids.addAll(List.of("PLT-Q-A1-L-0001-V3", "PLT-Q-A2-L-0001-V3", "PLT-Q-B1-L-0001-V3", "PLT-Q-B1-L-0002-V3",
                "PLT-Q-B2-L-0003-V3", "PLT-Q-B2-L-0004-V3"));
        return ids;
    }

    private static Map<String,Object> envelope(String name, String description, String sourceSha, String collection, Object docs) {
        return object("metadata", object("name", name, "version", 1, "description", description,
                "source_sha256", sourceSha), "collections", object(collection, docs));
    }

    private static LinkedHashMap<String,Object> object(Object... values) {
        var result = new LinkedHashMap<String,Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i+1]);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> parseObject(String text) { return (Map<String,Object>) JSON.readValue(text, Map.class); }
    private static Map<String,Object> readObject(Path file) throws Exception { return parseObject(Files.readString(file)); }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> readArray(Path file) throws Exception { return (List<Map<String,Object>>) JSON.readValue(Files.readString(file), List.class); }
    private static String text(Object value) { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value).replace("\r\n", "\n") + "\n"; }
    private static String sha(Path file) throws Exception { return sha(Files.readAllBytes(file)); }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static void verifyFileInfos(Object value) throws Exception {
        if (value instanceof Map<?,?> map) {
            if (map.get("path") instanceof String path && map.get("sha256") instanceof String expected
                    && map.get("bytes") instanceof Number bytes) {
                Path file = path.startsWith("engolearn-backend/") ? BACKEND.resolve(path.substring("engolearn-backend/".length())) : REPO.resolve(path);
                require(Files.size(file) == bytes.longValue() && sha(file).equals(expected), "M1 manifest integrity: " + path);
            } else for (Object nested : map.values()) verifyFileInfos(nested);
        } else if (value instanceof List<?> list) for (Object nested : list) verifyFileInfos(nested);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
