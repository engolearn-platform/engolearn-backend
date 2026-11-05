package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.service.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionMessageRepository;
import fit.iuh.engolearn.subscription.infra.scheduling.SubscriptionActivationScheduler;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfDockerAvailable
@Import(SubscriptionIntegrationTest.TimeConfig.class)
@TestPropertySource(properties={"engolearn.security.dev-auth.enabled=true","engolearn.subscription.activation-delay-ms=3600000"})
class SubscriptionIntegrationTest extends AbstractMongoIntegrationTest {
    static final Instant START=Instant.parse("2026-10-28T12:00:00Z");
    static class MutableClock extends Clock {
        volatile Instant now=START;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock testClock(){return new MutableClock();}}
    @Autowired SubscriptionPublicationService publication;
    @Autowired SubscriptionService service;
    @Autowired EffectiveEntitlementProvider entitlements;
    @Autowired SubscriptionTransactions transactions;
    @Autowired SubscriptionMessageRepository messages;
    @Autowired MutableClock clock;
    @Autowired SubscriptionActivationScheduler scheduler;
    @BeforeEach void setup(){clock.now=START;messages.ensureStorage();publication.publish(manifest("initial",List.of(policy("free1","FREE",1,START.minusSeconds(1),2),policy("trial1","TRIAL",1,START.minusSeconds(1),5)),true));}
    PublicationManifestRequest.PolicyRequest policy(String id,String scope,long version,Instant at,long limit){
        return new PublicationManifestRequest.PolicyRequest(id,scope,version,at,new QuotaPolicySnapshotDto(id,scope,version,List.of(new QuotaRuleDto("TEST","Test",limit,"ATTEMPT","MONTHLY",0))),new CurrentBenefitsResponse(scope.equals("FREE")?"FREE":"PREMIUM",scope,id,List.of(new PlanBenefitResponse("TEST","Test","Approved test benefit",null,0))));
    }
    PublicationManifestRequest manifest(String id,List<PublicationManifestRequest.PolicyRequest> policies,boolean account){
        return new PublicationManifestRequest(id,"isolated-test",account?List.of(new PublicationManifestRequest.PlanRequest(id+"-offer","FREE",1,START.minusSeconds(1),true,0,new PlanCardResponse("FREE","Free",null,List.of(),List.of()))):List.of(),policies,account?List.of(new PublicationManifestRequest.AccountHistoryRequest("u",false,"NEW_ACCOUNT")):List.of());
    }
    @Test void missingHistoryFailsAndEffectiveFreeIsConfirmed(){
        assertThatThrownBy(()->service.summary("unknown")).isInstanceOf(SubscriptionUnavailableException.class);
        assertThat(service.summary("u").tier()).isEqualTo("FREE");assertThat(service.summary("u").trialEligible()).isTrue();
        assertThat(service.plans()).hasSize(1);
    }
    @Test void concurrentTrialCreatesOneLifetimePeriodAndReplaysAfterExpiry() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        try {
            Callable<TrialActivationResponse> a=()->{start.await(10,TimeUnit.SECONDS);return service.activateTrial("u",first).activation();};
            Callable<TrialActivationResponse> b=()->{start.await(10,TimeUnit.SECONDS);return service.activateTrial("u",second).activation();};
            Future<TrialActivationResponse> f1=pool.submit(a),f2=pool.submit(b);start.countDown();
            TrialActivationResponse original=f1.get(20,TimeUnit.SECONDS);assertThat(f2.get(20,TimeUnit.SECONDS)).isEqualTo(original);
            assertThat(mongoTemplate.getCollection("subscription_periods").countDocuments()).isEqualTo(1);
            assertThat(mongoTemplate.getCollection("subscription_operation_receipts").countDocuments()).isEqualTo(2);
            assertThat(original.periodEndAt()).isEqualTo(START.plus(Duration.ofDays(7)));
            clock.now=original.periodEndAt();assertThat(service.summary("u").tier()).isEqualTo("FREE");
            assertThat(service.activateTrial("u",first).activation()).isEqualTo(original);
            assertThat(service.activateTrial("u",second).activation()).isEqualTo(original);
            assertThatThrownBy(()->service.activateTrial("u",UUID.randomUUID().toString())).hasMessageContaining("already been used");
        } finally {pool.shutdownNow();}
    }
    @Test void callerFailureRollsBackTrialMarkerPeriodAndReceipt(){
        assertThatThrownBy(()->transactions.execute(()->{service.activateTrial("u",UUID.randomUUID().toString());throw new IllegalStateException("after activation");})).isInstanceOf(IllegalStateException.class);
        assertThat(mongoTemplate.findById("u",SubscriptionAccount.class).isTrialUsed()).isFalse();
        assertThat(mongoTemplate.getCollection("subscription_periods").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("subscription_operation_receipts").countDocuments()).isZero();
    }
    @Test void immutableTrialSnapshotAndFreeTimeSelectionWorkWithoutExpiryJob(){
        TrialActivationResponse trial=service.activateTrial("u",UUID.randomUUID().toString()).activation();
        Instant midnight=START.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        publication.publish(manifest("update",List.of(policy("free2","FREE",2,midnight,7),policy("trial2","TRIAL",2,midnight,9)),false));
        clock.now=midnight;assertThat(entitlements.resolveEffectiveEntitlement("u",clock.instant()).policySnapshotId()).isEqualTo("trial1");
        scheduler.activateDue();assertThat(service.summary("u").currentBenefits().policySnapshotId()).isEqualTo("trial1");
        clock.now=trial.periodEndAt();assertThat(entitlements.resolveEffectiveEntitlement("u",clock.instant()).policySnapshotId()).isEqualTo("free2");
    }
    @Test void identicalManifestIsIdempotentAndChangedReuseRejected(){
        PublicationManifestRequest same=manifest("initial",List.of(policy("free1","FREE",1,START.minusSeconds(1),2),policy("trial1","TRIAL",1,START.minusSeconds(1),5)),true);
        publication.publish(same);
        assertThatThrownBy(()->publication.publish(new PublicationManifestRequest("initial","different",same.plans(),same.policies(),same.accounts()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(mongoTemplate.getCollection("subscription_quota_policy_versions").countDocuments()).isEqualTo(2);
    }
    @Test void highResolutionClockActivationAndPersistedReplayHaveExactlyEqualResults(){
        clock.now=START.plusNanos(123_456_789);
        String key=UUID.randomUUID().toString();
        TrialActivationResponse original=service.activateTrial("u",key).activation();
        TrialActivationResponse replay=service.activateTrial("u",key).activation();
        assertThat(replay).isEqualTo(original);
        assertThat(original.periodStartAt()).isEqualTo(Instant.ofEpochMilli(clock.millis()));
        assertThat(original.periodEndAt()).isEqualTo(original.periodStartAt().plus(Duration.ofDays(7)));
    }
}
