package fit.iuh.engolearn.assessment;

import fit.iuh.engolearn.assessment.contract.AssessmentGrantRevocation;
import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.AssessmentStateResponse;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.assessment.service.*;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.service.SubscriptionPublicationService;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import java.io.ByteArrayInputStream;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"engolearn.security.jwt.secret=isolated-assessment-test-secret-at-least-32-bytes", "engolearn.security.cookies.secure=false", "engolearn.messaging.enabled=false", "engolearn.assessment.expiry-delay-ms=3600000","engolearn.subscription.activation-delay-ms=3600000","engolearn.mongodb.bootstrap.enabled=false","spring.mongodb.username=","spring.mongodb.password="})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AssessmentMongoIntegrationTest.TimeConfiguration.class)
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
    @Autowired AssessmentService service;
    @Autowired AssessmentRepository store;
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
        publications.publish(new PublicationManifestRequest(manifest.manifestId(), manifest.environment(), manifest.plans(), manifest.policies(),
                List.of(new PublicationManifestRequest.AccountHistoryRequest(USER, false, "NEW_ACCOUNT"))));
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
        assertThat(store.profile(USER)).isNull();assertThat(remaining()).isEqualTo(1);
    }
    @Test void missingAudioRollsBackStartAndDoesNotCharge() throws Exception {
        Files.delete(MEDIA.resolve("unit14.wav"));
        assertThatThrownBy(this::start).isInstanceOfSatisfying(AssessmentException.class,e->assertThat(e.code()).isEqualTo("MEDIA_UNAVAILABLE"));
        assertThat(mongo.getCollection("assessment_attempts").countDocuments()).isZero();assertThat(remaining()).isEqualTo(1);
    }
    @Test void quotaDenialAfterAbandonDoesNotCreateAnotherAttemptOrResetCooldown() {
        var a=start();service.abandon(USER,a.id());assertThat(store.profile(USER).getLatestValidAt()).isNull();
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
        assertThat(store.profile(USER).getLatestValidAt()).isNull();assertThat(remaining()).isZero();
    }
    @Test void deadlineTakesPrecedenceOverLateAbandonAndUsesDeadlineAsCooldownOrigin() {
        var a=start();save(a,1,1,1);clock.at=START.plusSeconds(4000);
        assertThat(service.abandon(USER,a.id()).status()).isEqualTo("READY");
        assertThat(service.result(USER,a.id()).submittedAt()).isEqualTo(START.plusSeconds(3600));
        assertThat(store.profile(USER).getLatestValidAt()).isEqualTo(START.plusSeconds(3600));
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
