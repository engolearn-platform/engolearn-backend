package fit.iuh.engolearn.assessment;

import fit.iuh.engolearn.assessment.contract.AssessmentGrantRevocation;
import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.AssessmentStateResponse;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.assessment.service.*;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.service.SubscriptionPublicationService;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import fit.iuh.engolearn.user.contract.UserAssessmentProgress;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"engolearn.security.jwt.secret=isolated-assessment-test-secret-at-least-32-bytes", "engolearn.security.cookies.secure=false", "engolearn.messaging.enabled=false", "engolearn.assessment.expiry-delay-ms=3600000","engolearn.subscription.activation-delay-ms=3600000","engolearn.mongodb.bootstrap.enabled=false","spring.mongodb.username=","spring.mongodb.password="})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({AssessmentMongoIntegrationTest.TimeConfiguration.class, AssessmentStorageTestSupport.class})
@EnabledIfEnvironmentVariable(named="ASSESSMENT_TEST_MONGO_URI",matches=".+")
class AssessmentMongoIntegrationTest {
    String USER;
    jakarta.servlet.http.Cookie userCookie;
    jakarta.servlet.http.Cookie otherCookie;
    static final Instant START=Instant.parse("2026-10-30T00:00:00Z");
    static final Path MEDIA=Path.of(System.getProperty("java.io.tmpdir"),"engolearn-assessment-test-media");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.mongodb.uri",()->System.getenv("ASSESSMENT_TEST_MONGO_URI"));
        r.add("spring.mongodb.database",()->"engolearn_pfc_test");
        r.add("engolearn.assessment.media-directory",()->MEDIA.toString());
    }
    static class MutableClock extends Clock {
        volatile Instant at=START;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return at;}
    }
    @TestConfiguration static class TimeConfiguration {@Bean @Primary MutableClock assessmentTestClock(){return new MutableClock();}}
    @Autowired MutableClock clock;
    @Autowired MongoTemplate mongo;
    @Autowired MediaManagement mediaManagement;
    @Autowired AssessmentStorageTestSupport.LocalStorage storage;
    @Autowired AssessmentService service;
    @MockitoSpyBean AssessmentMedia media;
    @Autowired AssessmentRepository store;
    @Autowired UserAssessmentProgress progress;
    @Autowired AssessmentGrantRevocation revocations;
    @Autowired SubscriptionPublicationService publications;
    @Autowired SubscriptionService subscriptions;
    @Autowired fit.iuh.engolearn.shared.messaging.DurableMessageDispatcher dispatcher;
    @Autowired QuotaAvailability quota;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    String binding;
    AssessmentPaper legacyPaper;
    @BeforeEach void setup() throws Exception {
        assertThat(mongo.getDb().getName()).isEqualTo("engolearn_pfc_test");
        for(String collection:mongo.getCollectionNames()) if(collection.startsWith("assessment_")||collection.startsWith("quota_")||collection.startsWith("subscription_")) mongo.remove(new Query(),collection);
        storage.reset();
        clock.at=START;binding=UUID.randomUUID().toString();
        var registered = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"email\":\"pfc-" + UUID.randomUUID() + "@example.test\",\"password\":\"Isolated-test-123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        USER = mapper.readTree(registered.getContentAsString()).path("data").path("id").asString();
        userCookie = registered.getCookie("ACCESS_TOKEN");
        var other = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"email\":\"other-" + UUID.randomUUID() + "@example.test\",\"password\":\"Isolated-test-123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        otherCookie = other.getCookie("ACCESS_TOKEN");
        var manifest = mapper.readValue(Files.readString(Path.of("tools/subscription/dev-main-manifest.json")),PublicationManifestRequest.class);
        // Registration now creates Subscription history. Publish only the catalog for this registered fixture.
        publications.publish(new PublicationManifestRequest(manifest.manifestId(), manifest.environment(), manifest.plans(), manifest.policies(), List.of()));
        Files.createDirectories(MEDIA);
        var format=new AudioFormat(16000,16,1,true,false);
        Path audio=MEDIA.resolve("sound-check.wav");
        try(var input=new AudioInputStream(new ByteArrayInputStream(new byte[3200]),format,1600)) {AudioSystem.write(input,AudioFileFormat.Type.WAVE,audio.toFile());}
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(audio)));
        for(int i=14;i<26;i++) Files.copy(audio,MEDIA.resolve("unit"+i+".wav"),StandardCopyOption.REPLACE_EXISTING);
        legacyPaper = AssessmentTestPaper.paper("paper-1","series-1",digest);
        var modern = AssessmentTestPaper.modernPaper();
        modern.getUnits().stream().filter(u -> u.getAudioPath() != null).forEach(u -> u.setAudioSha256(digest));
        mongo.save(modern);
        mongo.getCollection("assessment_dev_baselines").insertOne(new Document("_id",USER).append("valid",true).append("level","A2"));
    }
    AssessmentStartRequest command(){return new AssessmentStartRequest(UUID.randomUUID().toString(),binding,true,true);}
    AssessmentStateResponse start(){
        var started = service.start(USER,command());
        // Import a pre-upgrade active snapshot to retain the legacy regression journey.
        var existing = store.attempt(started.id(),USER);
        existing.setPaper(fit.iuh.engolearn.assessment.mapper.AssessmentMapper.snapshot(legacyPaper));
        AssessmentTimeline.open(existing,0,clock.instant());
        store.save(existing);
        return service.state(USER,started.id(),binding);
    }
    AssessmentAnswerRequest answer(int q,long sequence,Integer choice){return new AssessmentAnswerRequest(q,sequence,choice,UUID.randomUUID().toString());}
    AssessmentStateResponse save(AssessmentStateResponse a,int q,long sequence,Integer choice){return service.answer(USER,a.id(),binding,answer(q,sequence,choice),false);}
    long remaining(){return quota.checkAvailability(USER,"PROFICIENCY_CHECK_ATTEMPT").remaining();}
    @Test
    void paperArchivedWhileCheckingAudioCannotConsumeQuota() {
        var paper = store.published().getFirst();
        var archived = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (archived.compareAndSet(false, true)) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(paper.getId())),
                        new Update().set("published", false), AssessmentPaper.class);
            }
            return invocation.callRealMethod();
        }).when(media).verifyWav(any(), anyInt());

        assertThatThrownBy(() -> service.start(USER, command())).isInstanceOfSatisfying(
                AssessmentException.class, e -> assertThat(e.code()).isEqualTo("PAPER_CHANGED"));
        assertThat(archived.get()).isTrue();
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isZero();
        assertThat(mongo.getCollection("quota_usage_ledger").countDocuments()).isZero();
        assertThat(remaining()).isEqualTo(1);
    }

    @Test
    void missingHistoricalUserProfileReturnsRetryableErrorAndKeepsSavedAnswers() throws Exception {
        var paper = store.published().getFirst();
        paper.setDemonstration(false);
        mongo.save(paper);
        var attempt = startModern();
        clock.at = START.plusSeconds(2);
        save(attempt, 1, 1, 1);
        var profileType = fit.iuh.engolearn.user.model.UserProfile.class;
        var profile = mongo.findById(USER, profileType);
        // Simulate data imported before account registration created a profile.
        assertThat(profile).isNotNull();
        mongo.remove(profile);
        String path = "/api/v1/assessment/attempts/" + attempt.id() + "/submit";

        mvc.perform(post(path).cookie(userCookie).header("X-Assessment-Binding", binding)
                        .contentType("application/json").content("{\"confirmed\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_LEVEL_SYNC_UNAVAILABLE"));
        assertThat(store.attempt(attempt.id(), USER).getStatus().name()).isEqualTo("ACTIVE");
        assertThat(store.attempt(attempt.id(), USER).getAnswers()).hasSize(1);

        mongo.insert(profile);
        mvc.perform(post(path).cookie(userCookie).header("X-Assessment-Binding", binding)
                        .contentType("application/json").content("{\"confirmed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
        assertThat(remaining()).isZero();
    }

    @Test
    void aRealOwnedPlacementResultEnablesAssessmentWithoutDevelopmentBaseline() {
        mongo.getCollection("assessment_dev_baselines").deleteOne(new Document("_id", USER));
        assertThat(service.preparation(USER).baselineValid()).isFalse();
        var result = new Document("_id", UUID.randomUUID().toString()).append("user_id", "another-learner-" + UUID.randomUUID())
                .append("status", "SUBMITTED").append("correct_count", 18)
                .append("placement_level", "B2").append("started_at", Date.from(START.minusSeconds(90)));
        mongo.getCollection("placement_attempts").insertOne(result);
        assertThat(service.preparation(USER).baselineValid()).isFalse();
        result.put("_id", UUID.randomUUID().toString());
        result.put("user_id", USER);
        mongo.getCollection("placement_attempts").insertOne(result);
        assertThat(service.preparation(USER).baselineValid()).isTrue();
        assertThat(service.start(USER, command()).status()).isEqualTo("ACTIVE");
        assertThat(service.preparation(USER).initialLevel()).isEqualTo("B2");
    }

    @Test
    void submittedPlacementA1EnablesImmediateFirstPeriodicAttemptOnlyForItsOwner() throws Exception {
        checkOpening(OpeningCase.RECOVERY);
    }

    @Test void truncatedObjectBeforeStartDoesNotConsumeQuota() throws Exception {
        checkOpening(OpeningCase.TRUNCATE);
    }

    @Test void reviewAfterReplacementStillUsesOriginalMediaAndStartReplayIsFree() throws Exception {
        checkOpening(OpeningCase.REVIEW);
    }

    private enum OpeningCase { RECOVERY, TRUNCATE, REVIEW }

    private void checkOpening(OpeningCase openingCase) throws Exception {
        mongo.getCollection("assessment_dev_baselines").deleteOne(new Document("_id", USER));
        PlacementTestSet placementSet;
        try (var sample = new ClassPathResource("placement/sample-test-set.json").getInputStream()) {
            placementSet = mapper.readValue(sample, PlacementTestSet.class);
        }
        mongo.save(placementSet);
        var first = placementSet.getItems().getFirst();
        String placementPath = "/api/v1/placement/attempts";
        String assessmentPath = "/api/v1/assessment";
        String startBody = mapper.writeValueAsString(command());

        var started = mvc.perform(post(placementPath).cookie(userCookie))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String placementId = mapper.readTree(started.getContentAsString()).path("data").path("attemptId").asString();
        mvc.perform(get(assessmentPath + "/preparation").cookie(userCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baselineValid").value(false));
        mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                        .contentType("application/json").content(startBody))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BASELINE_REQUIRED"));

        String answer = mapper.writeValueAsString(Map.of("questionId", first.getId(),
                "selectedOptionId", first.getCorrectOptionId()));
        mvc.perform(patch(placementPath + "/" + placementId + "/answers").cookie(userCookie)
                        .contentType("application/json").content(answer))
                .andExpect(status().isOk());
        mvc.perform(post(placementPath + "/" + placementId + "/submit").cookie(userCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(get(placementPath + "/" + placementId + "/result").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(1))
                .andExpect(jsonPath("$.data.totalQuestions").value(20))
                .andExpect(jsonPath("$.data.placementLevel").value("A1"));

        mvc.perform(get(assessmentPath + "/preparation").cookie(otherCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baselineValid").value(false));
        mvc.perform(post(assessmentPath + "/attempts").cookie(otherCookie)
                        .contentType("application/json").content(startBody))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BASELINE_REQUIRED"));

        mvc.perform(get(assessmentPath + "/preparation").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baselineValid").value(true))
                .andExpect(jsonPath("$.data.cooldownSatisfied").value(true))
                .andExpect(jsonPath("$.data.paperAvailable").value(true))
                .andExpect(jsonPath("$.data.initialLevel").value("A1"));
        var published = store.published().getFirst();
        var unit = published.getUnits().getFirst();
        byte[] wav = Files.readAllBytes(MEDIA.resolve(unit.getAudioPath()));
        var asset = mediaManagement.upload(new UploadMediaCommandDto("ASSESSMENT_AUDIO", MediaAccessPolicy.PROTECTED,
                "first-question.wav", "audio/wav", wav.length, new ByteArrayInputStream(wav)));
        mongo.save(new AssessmentAudioAsset(unit.getAudioSha256(), asset.mediaId(), 0.1));
        unit.setAudioMediaId(asset.mediaId());
        mongo.save(published);

        var url = mediaManagement.resolve(asset.mediaId()).url();
        var getWav = HttpRequest.newBuilder(url).build();
        var http = HttpClient.newHttpClient();
        assertThat(http.send(getWav, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        storage.setAvailable(url, false);
        assertThat(http.send(getWav, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        mvc.perform(get(assessmentPath + "/sound-check").cookie(userCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base64").isNotEmpty());
        assertThat(remaining()).isEqualTo(1);
        mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                        .contentType("application/json").content(startBody))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MEDIA_UNAVAILABLE"));
        var attempts = mongo.getCollection("assessment_attempts");
        var usage = mongo.getCollection("quota_usage_ledger");
        assertThat(attempts.countDocuments()).isZero();
        assertThat(usage.countDocuments()).isZero();
        assertThat(remaining()).isEqualTo(1);

        storage.setAvailable(url, true);
        if (openingCase == OpeningCase.TRUNCATE) {
            storage.replaceObject(url, Arrays.copyOf(wav, 12));
            var full = http.send(getWav, HttpResponse.BodyHandlers.ofByteArray());
            var range = http.send(HttpRequest.newBuilder(url).header("Range","bytes=0-11").build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(range.headers().firstValue("Content-Range")).contains("bytes 0-11/12");
            assertThat(full.body()).hasSize(12);
            var result = mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                    .contentType("application/json").content(startBody)).andReturn().getResponse();
            System.out.printf("AUDIT truncated-start full=%d/%d range=%d/%s start=%d status=%s attempts=%d ledger=%d quota=%d%n",
                full.statusCode(), full.body().length, range.statusCode(), range.headers().firstValue("Content-Range").orElse("?"),
                result.getStatus(),mapper.readTree(result.getContentAsString()).at("/data/status").asString(),attempts.countDocuments(),usage.countDocuments(),remaining());
            assertThat(result.getStatus()).isEqualTo(503);
            assertThat(mapper.readTree(result.getContentAsString()).path("code").asString()).isEqualTo("MEDIA_UNAVAILABLE");
            assertThat(remaining()).isEqualTo(1);
            assertThat(attempts.countDocuments()).isZero();
            assertThat(usage.countDocuments()).isZero();
            storage.replaceObject(url, wav);
        }
        var accepted = mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                        .contentType("application/json").content(startBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse();
        String attemptId = mapper.readTree(accepted.getContentAsString()).at("/data/id").asString();
        mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                        .contentType("application/json").content(startBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(attemptId));
        assertThat(remaining()).isZero();
        assertThat(attempts.countDocuments()).isEqualTo(1);
        assertThat(usage.countDocuments()).isEqualTo(1);
        System.out.printf("PFC start-recovery mode=%s attempt=%s status=ACTIVE attempts=1 ledger=1 remaining=0%n",
                openingCase, attemptId);
        if (openingCase == OpeningCase.REVIEW) {
            storage.setAvailable(url,false);
            var replay = mvc.perform(post(assessmentPath + "/attempts").cookie(userCookie)
                    .contentType("application/json").content(startBody))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(attemptId)).andReturn().getResponse();
            var replayState=mapper.readTree(replay.getContentAsString()).path("data");
            var startState=mapper.readTree(accepted.getContentAsString()).path("data");
            assertThat(replayState.path("startedAt")).isEqualTo(startState.path("startedAt"));
            assertThat(replayState.path("deadline")).isEqualTo(startState.path("deadline"));
            assertThat(usage.countDocuments()).isEqualTo(1);
            assertThat(attempts.countDocuments()).isEqualTo(1);

            byte[] replacement = wav.clone();
            replacement[replacement.length-1] ^= 1;
            var sha=MessageDigest.getInstance("SHA-256");
            String newDigest = HexFormat.of().formatHex(sha.digest(replacement));
            var asset2 = mediaManagement.upload(new UploadMediaCommandDto("ASSESSMENT_AUDIO", MediaAccessPolicy.PROTECTED,
                    "replacement.wav","audio/wav",replacement.length,new ByteArrayInputStream(replacement)));
            mongo.save(new AssessmentAudioAsset(newDigest,asset2.mediaId(),0.1));
            int oldVersion = published.getVersion();
            published.setPublished(false);
            mongo.save(published);
            published.setId(published.getId()+"-new");
            published.setPublished(true);
            published.setVersion(oldVersion+1);
            published.getUnits().getFirst().setAudioMediaId(asset2.mediaId());
            published.getUnits().getFirst().setAudioSha256(newDigest);
            mongo.save(published);

            clock.at=START.plusSeconds(2);
            assertThat(service.answer(USER,attemptId,binding,answer(1,1,0),false).savedCount()).isEqualTo(1);
            assertThat(service.submit(USER,attemptId,binding).status()).isEqualTo("READY");
            String reviewPath=assessmentPath+"/attempts/"+attemptId+"/review/audio/0";
            mvc.perform(get(reviewPath).cookie(otherCookie)).andExpect(status().isNotFound());
            var firstReview=mvc.perform(get(reviewPath).cookie(userCookie)).andExpect(status().isOk()).andReturn().getResponse();
            var firstUri=URI.create(mapper.readTree(firstReview.getContentAsString()).at("/data/url").asString());
            assertThat(http.send(HttpRequest.newBuilder(firstUri).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            var restoredUri = storage.recoverUnderSameMediaId(mongo, url, wav);
            var retry=mvc.perform(get(reviewPath).cookie(userCookie)).andExpect(status().isOk()).andReturn().getResponse();
            var retryUri=URI.create(mapper.readTree(retry.getContentAsString()).at("/data/url").asString());
            assertThat(retryUri).isEqualTo(restoredUri);
            var recovered=http.send(HttpRequest.newBuilder(retryUri).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(recovered.statusCode()).isEqualTo(200);
            assertThat(recovered.body()).containsExactly(wav);
            assertThat(recovered.body()).isNotEqualTo(replacement);
            assertThat(store.attempt(attemptId,USER).getPaper().getVersion()).isEqualTo(oldVersion);
            assertThat(store.published().getFirst().getVersion()).isEqualTo(oldVersion+1);
            System.out.printf("AUDIT history activeReplay=200 sameIdStartDeadline=true attempts=%d ledger=%d remaining=%d oldVersion=%d newVersion=%d foreign=404 oldWav=404 recovered=200 sha=%s newSha=%s%n",
                    attempts.countDocuments(),usage.countDocuments(),remaining(),oldVersion,oldVersion+1,
                    HexFormat.of().formatHex(sha.digest(recovered.body())),newDigest);
        }
    }

    @Test
    void ownedPlacementResultDoesNotReplaceAnUnavailablePeriodicPaper() {
        mongo.getCollection("assessment_dev_baselines").deleteOne(new Document("_id", USER));
        mongo.getCollection("placement_attempts").insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("user_id", USER).append("status", "SUBMITTED").append("correct_count", 18)
                .append("placement_level", "B2").append("started_at", Date.from(START.minusSeconds(90))));
        mongo.remove(new Query(), AssessmentPaper.class);

        var preparation = service.preparation(USER);
        assertThat(preparation.baselineValid()).isTrue();
        assertThat(preparation.paperAvailable()).isFalse();
        assertThatThrownBy(() -> service.start(USER, command())).isInstanceOfSatisfying(
                AssessmentException.class, e -> assertThat(e.code()).isEqualTo("NO_PAPER"));
        assertThat(remaining()).isEqualTo(1);
    }

    @Test
    void submissionMigratesLegacyLevelsIntoExistingUserProfile() {
        var paper = store.published().getFirst();
        paper.setDemonstration(false);
        mongo.save(paper);
        var attempt = startModern();
        clock.at = START.plusSeconds(2);
        save(attempt, 1, 1, 1);

        var profileType = fit.iuh.engolearn.user.model.UserProfile.class;
        var account = Query.query(Criteria.where("_id").is(USER));
        var update = new Update().unset("assessment_progress").set("display_name", "Profile name")
                .set("cefr_level", "B2");
        assertThat(mongo.updateFirst(account, update, profileType).getMatchedCount()).isEqualTo(1);
        var historicalTime = START.minus(Duration.ofDays(22));
        var legacy = new Document("user_id", USER).append("initial_level", "A2")
                .append("current_level", "B1").append("highest_level", "B1")
                .append("latest_valid_at", Date.from(historicalTime))
                .append("_class", "fit.iuh.engolearn.assessment.model.AssessmentProfile");
        mongo.getCollection("assessment_profiles").insertOne(legacy);
        assertThat(service.preparation(USER).highestLevel()).isEqualTo("B1");

        assertThat(service.submit(USER, attempt.id(), binding).status()).isEqualTo("READY");
        var profile = mongo.findById(USER, profileType);
        var periodic = profile.getAssessmentProgress();
        assertThat(profile.getDisplayName()).isEqualTo("Profile name");
        assertThat(profile.getCefrLevel().name()).isEqualTo("B2");
        assertThat(periodic.initialLevel()).isEqualTo("A2");
        assertThat(periodic.currentLevel()).isEqualTo("A1");
        assertThat(periodic.highestLevel()).isEqualTo("B1");
        assertThat(periodic.latestValidAttemptId()).isEqualTo(attempt.id());
        assertThat(mongo.getCollection("assessment_profiles").find(new Document("user_id", USER)).first())
                .isEqualTo(legacy);
        assertThat(service.result(USER, attempt.id()).currentLevel()).isEqualTo("A1");
    }

    @Test
    void verifiedRefundDeliveryIsIdempotentAndOnlyRevokesItsCorrelatedGrant() {
        var consumer = new fit.iuh.engolearn.assessment.infra.event.AssessmentRefundConsumer(revocations, mapper);
        var refunded = new fit.iuh.engolearn.subscription.contract.event.PremiumPeriodRefundedEvent(USER, "snapshot-1", START);
        var message = new fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto(
                UUID.randomUUID().toString(), "PREMIUM_PERIOD_REFUNDED", 1, USER, consumer.recipient(), START,
                mapper.writeValueAsString(refunded));
        consumer.consume(message);
        consumer.consume(message);
        assertThat(store.revoked(USER, "PREMIUM:snapshot-1:" + START)).isTrue();
        assertThat(store.revoked(USER, "PREMIUM:snapshot-2:" + START)).isFalse();
        assertThat(store.revoked(USER, "TRIAL:snapshot-1:" + START)).isFalse();
    }

    @Test void identicalConcurrentStartsCreateOneAttemptAndOneUsage() throws Exception {
        var command=command();var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{go.await();return service.start(USER,command);});
            var two=pool.submit(()->{go.await();return service.start(USER,command);});go.countDown();
            assertThat(one.get().id()).isEqualTo(two.get().id());
            assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isEqualTo(1);
            assertThat(remaining()).isZero();
        } finally {pool.shutdownNow();}
    }
    @Test void distinctStartWhileActiveDoesNotConsumeSecondUsage() {
        start();assertThatThrownBy(this::start).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("ACTIVE_ATTEMPT"));
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isEqualTo(1);assertThat(remaining()).isZero();
    }
    @Test void fullFiftyQuestionJourneyGradesExactlyThirtyTwoCorrect() {
        AssessmentStateResponse a=start();
        for(int guard=0;guard<100&&a.status().equals("ACTIVE");guard++) {
            if(a.current()==null) {clock.at=a.deadline();a=service.state(USER,a.id(),binding);}
            else a=service.answer(USER,a.id(),binding,answer(a.current().number(),a.current().number(),a.current().number()<=32?1:0),true);
        }
        assertThat(a.status()).isEqualTo("READY");
        var result=service.result(USER,a.id());assertThat(result.correct()).isEqualTo(32);assertThat(result.score()).isEqualTo(64);
        assertThat(result.readingCorrect()).isEqualTo(30);assertThat(result.listeningCorrect()).isEqualTo(2);
        assertThat(result.level()).isEqualTo("B1");assertThat(store.valid(USER)).hasSize(1);
    }
    @Test void attemptPersistenceFailureAfterConsumptionRollsBackQuotaAndProfile() {
        mongo.executeCommand(new Document("collMod","assessment_attempts").append("validator",new Document("$jsonSchema",new Document("bsonType","object").append("required",List.of("impossibleTestField")))));
        try {assertThatThrownBy(this::start).isInstanceOf(RuntimeException.class);}
        finally {mongo.executeCommand(new Document("collMod","assessment_attempts").append("validator",new Document()));}
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isZero();
        assertThat(progress.find(USER, true)).isEmpty();assertThat(remaining()).isEqualTo(1);
    }
    @Test void missingAudioRollsBackStartAndDoesNotCharge() throws Exception {
        Files.delete(MEDIA.resolve("unit14.wav"));
        assertThatThrownBy(this::start).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("MEDIA_UNAVAILABLE"));
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isZero();assertThat(remaining()).isEqualTo(1);
    }
    @Test void quotaDenialAfterAbandonDoesNotCreateAnotherAttemptOrResetCooldown() {
        var a=start();service.abandon(USER,a.id());assertThat(progress.find(USER, true).orElseThrow().latestValidAt()).isNull();
        assertThatThrownBy(this::start).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("QUOTA_EXHAUSTED"));
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isEqualTo(1);
    }
    @Test void newestAnswerWinsAndNextIsAtomicIdempotentAndForwardOnly() {
        var a=start();save(a,1,2,1);
        assertThatThrownBy(()->save(a,1,1,0)).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("STALE_ANSWER"));
        var next=answer(1,3,1);assertThat(service.answer(USER,a.id(),binding,next,true).current().number()).isEqualTo(2);
        assertThat(service.answer(USER,a.id(),binding,next,true).current().number()).isEqualTo(2);
        assertThatThrownBy(()->save(a,1,4,0)).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("QUESTION_LOCKED"));
    }
    @Test void readingPreviewRejectsAnswersAndGroupUsesSharedDeadline() {
        var a=start();for(int q=1;q<=10;q++) service.answer(USER,a.id(),binding,answer(q,q,null),true);
        assertThat(service.state(USER,a.id(),binding).phase()).isEqualTo("READING_PREVIEW");
        assertThatThrownBy(()->save(a,11,11,1)).isInstanceOf(AssessmentException.class);
        clock.at=START.plusSeconds(90);var answerWindow=service.state(USER,a.id(),binding);
        assertThat(answerWindow.deadline()).isEqualTo(START.plusSeconds(390));
        assertThat(service.answer(USER,a.id(),binding,answer(11,12,1),true).deadline()).isEqualTo(answerWindow.deadline());
    }
    @Test void singleAudioCanAdvanceAt15SecondsAndOldAudioIsNoLongerIssued() {
        var a=start();clock.at=START.plusSeconds(2400);var listen=service.state(USER,a.id(),binding);
        assertThat(listen.current().number()).isEqualTo(31);
        var request=new AssessmentAudioRequest(14,UUID.randomUUID().toString());assertThat(service.audio(USER,a.id(),binding,request).base64()).isNotBlank();
        assertThatThrownBy(()->service.audio(USER,a.id(),binding,new AssessmentAudioRequest(14,UUID.randomUUID().toString()))).isInstanceOf(AssessmentException.class);
        clock.at=START.plusSeconds(2415);var next=service.answer(USER,a.id(),binding,answer(31,1,1),true);
        assertThat(next.current().number()).isEqualTo(32);assertThat(next.deadline()).isEqualTo(START.plusSeconds(2475));
        assertThatThrownBy(()->service.audio(USER,a.id(),binding,request)).isInstanceOf(AssessmentException.class);
    }
    @Test void completedListeningGroupsAdvanceImmediatelyAndFinalRetryDoesNotRegrade() {
        var a=start();clock.at=START.plusSeconds(3000);
        assertThat(service.state(USER,a.id(),binding).phase()).isEqualTo("LISTENING_LEAD");
        for(int q=41;q<=44;q++) service.answer(USER,a.id(),binding,answer(q,q,1),true);
        var lastOfGroup=answer(45,45,1);
        var next=service.answer(USER,a.id(),binding,lastOfGroup,true);
        assertThat(next.current().number()).isEqualTo(46);assertThat(next.unitIndex()).isEqualTo(25);
        assertThat(next.deadline()).isEqualTo(clock.at.plusSeconds(120));
        assertThat(service.answer(USER,a.id(),binding,lastOfGroup,true).current().number()).isEqualTo(46);
        for(int q=46;q<=49;q++) service.answer(USER,a.id(),binding,answer(q,q,1),true);
        var finalNext=answer(50,50,0);
        assertThat(service.answer(USER,a.id(),binding,finalNext,true).status()).isEqualTo("READY");
        assertThat(service.answer(USER,a.id(),binding,finalNext,true).status()).isEqualTo("READY");
        var result=service.result(USER,a.id());
        assertThat(result.submittedAt()).isEqualTo(clock.at);assertThat(result.correct()).isEqualTo(9);
        assertThat(service.review(USER,a.id()).questions().getLast().selected()).isZero();
        assertThat(service.review(USER,a.id()).questions().getLast().correct()).isEqualTo(1);
        assertThat(service.history(USER,0,20)).hasSize(1);assertThat(remaining()).isZero();
    }
    @Test void readingPreviewEarlyFinishIsScopedIdempotentAndKeepsSharedDeadline() {
        var a=start();for(int q=1;q<=10;q++) service.answer(USER,a.id(),binding,answer(q,q,null),true);
        for(int index=10;index<=13;index++) {
            var command=new AssessmentFinishStageRequest(index,"READING_PREVIEW",UUID.randomUUID().toString());
            clock.at=clock.at.plusSeconds(5);var after=service.finishStage(USER,a.id(),binding,command);
            assertThat(after.phase()).isEqualTo("ANSWER");assertThat(after.deadline()).isEqualTo(clock.at.plusSeconds(300));
            clock.at=clock.at.plusSeconds(1);
            assertThat(service.finishStage(USER,a.id(),binding,command).deadline()).isEqualTo(after.deadline());
            for(int q=11+(index-10)*5;q<=15+(index-10)*5;q++) service.answer(USER,a.id(),binding,answer(q,q,1),true);
            var current=service.state(USER,a.id(),binding);
            assertThat(service.finishStage(USER,a.id(),binding,command).deadline()).isEqualTo(current.deadline());
            assertThat(service.state(USER,a.id(),binding).phase()).isEqualTo(current.phase());
        }
        assertThat(service.state(USER,a.id(),binding).current().number()).isEqualTo(31);assertThat(remaining()).isZero();
    }
    @Test void stalePreviewCommandDoesNotExtendExpiredWindow() {
        var a=start();for(int q=1;q<=10;q++) service.answer(USER,a.id(),binding,answer(q,q,null),true);
        clock.at=START.plusSeconds(90);
        assertThatThrownBy(()->service.finishStage(USER,a.id(),binding,new AssessmentFinishStageRequest(10,"READING_PREVIEW",UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("STAGE_CLOSED"));
        assertThat(service.state(USER,a.id(),binding).deadline()).isEqualTo(START.plusSeconds(390));
    }
    @Test void finishStageWebValidationOwnershipAndBindingAreEnforced() throws Exception {
        var a=start();for(int q=1;q<=10;q++) service.answer(USER,a.id(),binding,answer(q,q,null),true);
        String path="/api/v1/assessment/attempts/"+a.id()+"/finish-stage";
        String body=mapper.writeValueAsString(new AssessmentFinishStageRequest(10,"READING_PREVIEW",UUID.randomUUID().toString()));
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).cookie(otherCookie).header("X-Assessment-Binding",binding).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path).cookie(userCookie).header("X-Assessment-Binding",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isConflict());
        for(String invalid:List.of(body.replace("READING_PREVIEW","LISTENING_LEAD"),body.replace("10","26"),body.replace("10","10.5"),"{}"))
            mvc.perform(post(path).cookie(userCookie).header("X-Assessment-Binding",binding).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post(path).cookie(userCookie).header("X-Assessment-Binding",binding).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("ANSWER")).andExpect(jsonPath("$.data.current.number").value(11));
    }
    @Test void zeroScoreIsValidAndReviewUsesOriginalPaperEvenAfterReplacement() {
        var a=start();save(a,1,1,0);service.submit(USER,a.id(),binding);
        var first=service.result(USER,a.id());assertThat(first.score()).isZero();assertThat(first.level()).isEqualTo("A1");assertThat(first.skills()).hasSize(2);
        mongo.remove(new Query(),AssessmentPaper.class);
        assertThat(service.review(USER,a.id()).questions()).hasSize(50);
        assertThat(service.review(USER,a.id()).questions().getFirst().prompt()).isEqualTo("Question 1");
        assertThat(service.submit(USER,a.id(),binding).status()).isEqualTo("READY");
    }
    @Test void noAnswersHasNoScoreNoReviewAndNoNewCooldown() {
        var a=start();assertThat(service.submit(USER,a.id(),binding).status()).isEqualTo("NO_RESULT");
        assertThatThrownBy(()->service.result(USER,a.id())).isInstanceOf(AssessmentException.class);
        assertThatThrownBy(()->service.review(USER,a.id())).isInstanceOf(AssessmentException.class);
        var row=service.history(USER,0,20).getFirst();assertThat(row.score()).isNull();assertThat(row.level()).isNull();assertThat(row.series()).isNull();
        assertThat(progress.find(USER, true).orElseThrow().latestValidAt()).isNull();assertThat(remaining()).isZero();
    }
    @Test void deadlineTakesPrecedenceOverLateAbandonAndUsesDeadlineAsCooldownOrigin() {
        var a=start();save(a,1,1,1);clock.at=START.plusSeconds(4000);
        assertThat(service.abandon(USER,a.id()).status()).isEqualTo("READY");
        assertThat(service.result(USER,a.id()).submittedAt()).isEqualTo(START.plusSeconds(3600));
        assertThat(progress.find(USER, true).orElseThrow().latestValidAt()).isEqualTo(START.plusSeconds(3600));
    }
    @Test void cooldownBoundaryAndMonthResetAreIndependent() {
        var a=start();save(a,1,1,1);service.submit(USER,a.id(),binding);
        clock.at=START.plus(Duration.ofDays(21)).minusNanos(1);
        assertThatThrownBy(this::start).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("COOLDOWN"));
        clock.at=START.plus(Duration.ofDays(21));assertThat(start().status()).isEqualTo("ACTIVE");
    }
    @Test void grantPersistsAfterTrialExpiryAndOnlyRevokedSourceIsRemoved() {
        subscriptions.activateTrial(USER,UUID.randomUUID().toString());
        var a=start();save(a,1,1,1);service.submit(USER,a.id(),binding);
        assertThat(service.result(USER,a.id()).skills()).hasSize(5);
        String grant=store.attempt(a.id(),USER).getAnalysisGrants().iterator().next();
        clock.at=START.plus(Duration.ofDays(8));assertThat(service.result(USER,a.id()).skills()).hasSize(5);
        var attempt=store.attempt(a.id(),USER);attempt.getAnalysisGrants().add("other-verified-period");store.save(attempt);
        revocations.revoke(USER,grant);assertThat(service.result(USER,a.id()).skills()).hasSize(5);
        revocations.revoke(USER,"other-verified-period");assertThat(service.result(USER,a.id()).skills()).hasSize(2);
        assertThat(service.result(USER,a.id()).score()).isEqualTo(2);
    }
    @Test void trialUpgradeOpensOldFreeReportAndRetainsConsumedUsage() {
        var a=start();save(a,1,1,1);service.submit(USER,a.id(),binding);
        assertThat(service.result(USER,a.id()).skills()).hasSize(2);
        subscriptions.activateTrial(USER,UUID.randomUUID().toString());
        dispatcher.dispatch();
        assertThat(service.result(USER,a.id()).skills()).hasSize(5);
        assertThat(service.result(USER,a.id()).score()).isEqualTo(2);
        assertThat(remaining()).isEqualTo(1);
        assertThat(service.preparation(USER).cooldownSatisfied()).isFalse();
    }
    @Test void ownershipBindingAuthShapeAndCacheHeadersAreEnforcedByRealWebStack() throws Exception {
        var a=start();
        mvc.perform(get("/api/v1/assessment/preparation")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/assessment/attempts/"+a.id()).cookie(otherCookie).header("X-Assessment-Binding",binding)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/assessment/attempts/"+a.id()).cookie(userCookie).header("X-Assessment-Binding",UUID.randomUUID())).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/assessment/attempts/"+a.id()+"/answer").cookie(userCookie).header("X-Assessment-Binding",binding).contentType("application/json").content("{\"question\":51,\"sequence\":0,\"choice\":4,\"commandId\":\"invalid\"}")).andExpect(status().isBadRequest());
        var response=mvc.perform(get("/api/v1/assessment/attempts/"+a.id()).cookie(userCookie).header("X-Assessment-Binding",binding)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("correctOption","explanation","transcript","skillResults");
    }
    @Test void audioFailureIsRecordedWithoutChangingDeadlineOrRefundingUsage() {
        var attempt=start();clock.at=START.plusSeconds(2400);
        var before=service.state(USER,attempt.id(),binding);
        var after=service.reportAudioFailure(USER,attempt.id(),binding,new AssessmentAudioFailureRequest(14,"PLAYBACK_FAILED"));
        assertThat(after.deadline()).isEqualTo(before.deadline());
        assertThat(store.attempt(attempt.id(),USER).getAudioFailures()).containsEntry("14","PLAYBACK_FAILED");
        assertThat(remaining()).isZero();
        assertThatThrownBy(()->service.reportAudioFailure(USER,attempt.id(),UUID.randomUUID().toString(),new AssessmentAudioFailureRequest(14,"PLAYBACK_FAILED")))
                .isInstanceOf(AssessmentException.class);
    }
    @Test void historyPagesDoNotLoadQuestionSnapshotsAndRemainOwnerScoped() {
        subscriptions.activateTrial(USER,UUID.randomUUID().toString());
        var first=start();service.abandon(USER,first.id());
        var second=start();service.abandon(USER,second.id());
        var one=service.history(USER,0,1);var two=service.history(USER,1,1);
        assertThat(one).hasSize(1);assertThat(two).hasSize(1);
        assertThat(one.getFirst().id()).isNotEqualTo(two.getFirst().id());
        assertThat(store.history(USER,0,1,true).getFirst().getPaper().getUnits()).isNull();
        assertThat(service.history("another-account",0,20)).isEmpty();
    }
    @Test void fractionalMissingAndUnknownJsonFieldsAreRejectedWithoutSavingAnAnswer() throws Exception {
        var attempt=start();String id=UUID.randomUUID().toString();
        for(String body:List.of("{\"question\":1.5,\"sequence\":1,\"choice\":0,\"commandId\":\""+id+"\"}",
                "{\"question\":1,\"sequence\":1,\"choice\":0,\"commandId\":\""+id+"\",\"unexpected\":true}",
                "{\"question\":1,\"sequence\":1,\"commandId\":\""+id+"\"}")) {
            mvc.perform(put("/api/v1/assessment/attempts/"+attempt.id()+"/answer").cookie(userCookie)
                    .header("X-Assessment-Binding",binding).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertThat(store.attempt(attempt.id(),USER).getAnswers()).isEmpty();
    }

    AssessmentStateResponse startModern() {
        return service.start(USER,command());
    }
    @Test void newStartsRequireModernPaperWhileLegacyPublishedDataIsPreserved() {
        mongo.remove(new Query(),AssessmentPaper.class);
        mongo.save(legacyPaper);
        assertThat(service.preparation(USER).paperAvailable()).isFalse();
        assertThatThrownBy(() -> service.start(USER,command())).isInstanceOfSatisfying(AssessmentException.class,
                e -> assertThat(e.code()).isEqualTo("NO_PAPER"));
        assertThat(remaining()).isEqualTo(1);
        assertThat(store.published()).hasSize(1);
    }
    @Test void modernGroupAllowsArbitraryAnswersAndConfirmedCloseIsIdempotent() throws Exception {
        var a = startModern();
        clock.at = START.plusSeconds(300);
        a = service.state(USER, a.id(), binding);
        assertThat(a.current().number()).isEqualTo(11);
        assertThatThrownBy(() -> service.answer(USER, store.active(USER).getId(), binding, answer(11, 1, 1), false))
                .isInstanceOfSatisfying(AssessmentException.class, e -> assertThat(e.code()).isEqualTo("QUESTION_LOCKED"));
        clock.at = START.plusSeconds(302);
        for (int q : new int[]{15, 11, 14, 12, 13}) a = save(a, q, q, 1);
        assertThat(a.savedCount()).isEqualTo(5);
        assertThat(a.deadline()).isEqualTo(START.plusSeconds(600));
        var close = new AssessmentFinishStageRequest(10, "ANSWER", UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/assessment/attempts/" + a.id() + "/finish-stage").cookie(userCookie)
                .header("X-Assessment-Binding", binding).contentType("application/json").content(mapper.writeValueAsString(close)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.current.number").value(16));
        assertThat(service.finishStage(USER, a.id(), binding, close).current().number()).isEqualTo(16);
        assertThat(remaining()).isZero();
    }
    @Test void earlyNextKeepsBlanksAndCannotAdvanceTwiceOrEditWhileWaiting() {
        String id = startModern().id();
        clock.at = START.plusMillis(1999);
        assertThatThrownBy(() -> service.answer(USER, id, binding, answer(1, 1, 0), true))
                .isInstanceOfSatisfying(AssessmentException.class, e -> assertThat(e.code()).isEqualTo("QUESTION_LOCKED"));
        var skip = answer(1, 2, null);
        assertThat(service.answer(USER, id, binding, skip, true).current().number()).isEqualTo(2);
        assertThat(service.answer(USER, id, binding, skip, true).current().number()).isEqualTo(2);
        assertThatThrownBy(() -> service.answer(USER, id, binding, answer(1, 3, null), true))
                .isInstanceOf(AssessmentException.class);
        clock.at = clock.at.plusSeconds(2);
        var state = service.answer(USER, id, binding, answer(2, 4, 0), true);
        assertThat(state.current().number()).isEqualTo(3);
        assertThat(state.savedCount()).isEqualTo(1);
        assertThat(remaining()).isZero();
    }

    @Test void confirmedGroupCloseDuringWaitAndAcrossPhaseBoundaryKeepsCorrectUnit() {
        String id = startModern().id();
        clock.at = START.plusSeconds(300).plusMillis(900);
        var first = new AssessmentFinishStageRequest(10, "LISTENING_WAIT", UUID.randomUUID().toString());
        var second = new AssessmentFinishStageRequest(11, "LISTENING_WAIT", UUID.randomUUID().toString());
        assertThat(service.finishStage(USER, id, binding, first).current().number()).isEqualTo(16);
        clock.at = clock.at.plusSeconds(2);
        var reading = service.finishStage(USER, id, binding, second);
        assertThat(reading.current().number()).isEqualTo(21);
        assertThat(reading.deadline()).isEqualTo(clock.at.plusSeconds(1800));
        assertThat(service.finishStage(USER, id, binding, second).savedCount()).isZero();
        assertThat(remaining()).isZero();
    }

    @Test void modernReadingCanReviseEarlierAnswersAndQuestionFiftyDoesNotSubmit() {
        var a = startModern();
        clock.at = START.plusSeconds(900);
        a = save(a, 50, 1, 1);
        a = save(a, 21, 2, 0);
        a = save(a, 21, 3, 1);
        assertThat(a.status()).isEqualTo("ACTIVE");
        assertThat(a.deadline()).isEqualTo(START.plusSeconds(2700));
        assertThat(a.questions()).hasSize(30);
        assertThat(a.savedCount()).isEqualTo(2);
        String id = a.id();
        assertThatThrownBy(() -> service.answer(USER, id, binding, answer(20, 4, 1), false))
                .isInstanceOfSatisfying(AssessmentException.class, e -> assertThat(e.code()).isEqualTo("QUESTION_LOCKED"));
        assertThatThrownBy(() -> service.answer(USER, id, binding, answer(50, 5, 1), true))
                .isInstanceOfSatisfying(AssessmentException.class, e -> assertThat(e.code()).isEqualTo("STAGE_ACTION_REQUIRED"));
        clock.at = START.plusSeconds(2700);
        var terminal = save(a, 22, 6, 1);
        assertThat(terminal.status()).isEqualTo("READY");
        assertThat(service.result(USER, id).correct()).isEqualTo(2);
        assertThat(store.attempt(id, USER).getSubmittedAt()).isEqualTo(START.plusSeconds(2700));
        assertThat(store.attempt(id, USER).getPaper().getRulesVersion()).isEqualTo(2);
    }
    @Test void modernSingleWaitAndAudioIssueSurviveTransportRetryWithoutExtraTime() {
        var a = startModern();
        var command = new AssessmentAudioRequest(0, UUID.randomUUID().toString());
        assertThat(service.audio(USER, a.id(), binding, command).base64()).isNotBlank();
        assertThat(service.audio(USER, a.id(), binding, command).base64()).isNotBlank();
        assertThat(service.state(USER, a.id(), binding).deadline()).isEqualTo(START.plusSeconds(30));
        clock.at = START.plusSeconds(2);
        a = save(a, 1, 1, 1);
        clock.at = START.plusSeconds(30);
        a = service.state(USER, a.id(), binding);
        assertThat(a.current().number()).isEqualTo(2);
        String id = a.id();
        assertThatThrownBy(() -> service.audio(USER, id, binding, command)).isInstanceOf(AssessmentException.class);
        assertThat(store.attempt(id, USER).getAnswers()).containsEntry("1", 1);
    }
    @Test void devIdentityHeaderDoesNotAuthenticateAfterMainIntegration() throws Exception {
        mvc.perform(get("/api/v1/assessment/preparation").header("X-Dev-User-Id", "dev-user"))
                .andExpect(status().isUnauthorized());
    }
}
