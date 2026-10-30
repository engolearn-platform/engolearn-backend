package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import fit.iuh.engolearn.subscription.service.*;
import fit.iuh.engolearn.subscription.infra.scheduling.SubscriptionActivationScheduler;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
@Import(AdminConfigurationIntegrationTest.TimeConfig.class)
@TestPropertySource(properties={"engolearn.security.dev-auth.enabled=true","engolearn.subscription.activation-delay-ms=3600000"})
class AdminConfigurationIntegrationTest extends AbstractMongoIntegrationTest {
    static final Instant START=Instant.parse("2026-10-29T12:00:00.123Z");
    static class MutableClock extends Clock {
        volatile Instant at=START;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return at;}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock adminClock(){return new MutableClock();}}
    @Autowired AdminConfigurationService admin;
    @Autowired SubscriptionPublicationService operator;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionActivationScheduler scheduler;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean SubscriptionRepository store;
    @MockitoSpyBean SubscriptionTransactions transactions;
    @BeforeEach void setup(){
        clock.at=START;
        List<PlanBenefitResponse> benefits=List.of(new PlanBenefitResponse("B","Benefit","Description","",0));
        List<PublicationManifestRequest.PolicyRequest> policies=List.of(policy("FREE",benefits),policy("TRIAL",benefits));
        operator.publish(new PublicationManifestRequest("initial","isolated-test",List.of(new PublicationManifestRequest.PlanRequest("free-plan","FREE",1,START.minusMillis(1),true,0,new PlanCardResponse("FREE","FREE",null,benefits,List.of()))),policies,
            List.of(new PublicationManifestRequest.AccountHistoryRequest("u",false,"NEW_ACCOUNT"),new PublicationManifestRequest.AccountHistoryRequest("v",false,"NEW_ACCOUNT"))));
    }
    PublicationManifestRequest.PolicyRequest policy(String scope,List<PlanBenefitResponse> benefits){String id=scope+"-policy";return new PublicationManifestRequest.PolicyRequest(id,scope,1,START.minusMillis(1),new QuotaPolicySnapshotDto(id,scope,1,List.of(new QuotaRuleDto("TEST","Test",2,"ATTEMPT","MONTHLY",0))),new CurrentBenefitsResponse(scope.equals("FREE")?"FREE":"PREMIUM",scope,id,benefits));}
    AdminPublicationRequest input(String scope,String mode){ConfigurationContentRequest c=admin.configuration(scope).current().content();return new AdminPublicationRequest(admin.catalog().revision(),new PublicationEffectRequest(mode,null),c);}
    String key(){return UUID.randomUUID().toString();}
    @Test void atomicBundleRoundTripsNullableMetadataAndReplayAfterRevisionAndTimeAdvance(){
        clock.at=START.plusNanos(456789);
        AdminPublicationRequest input=input("FREE","NEXT_LOCAL_MIDNIGHT");String key=key();
        var first=admin.publish("actor",key,"FREE",input);assertThat(first.created()).isTrue();
        assertThat(first.result().acceptedAt()).isEqualTo(Instant.ofEpochMilli(clock.millis()));
        assertThat(admin.detail("FREE",2).content()).isEqualTo(admin.normalize(input).content());
        clock.at=first.result().effectiveAt();scheduler.activateDue();
        var replay=admin.publish("actor",key,"FREE",input);assertThat(replay.created()).isFalse();assertThat(replay.result()).isEqualTo(first.result());
        assertThat(store.readConfigurationRevision()).isEqualTo(first.result().revision()+1);
        assertThat(mongoTemplate.findAll(AdminPublicationReceipt.class)).hasSize(1);
        assertThatThrownBy(()->admin.publish("actor",key,"TRIAL",input)).hasMessageContaining("different content");
    }
    @Test void insertionFailureAfterPolicyRollsBackAllDocumentsAndRevision(){
        AdminPublicationRequest input=input("FREE","NEXT_LOCAL_MIDNIGHT");long revision=store.readConfigurationRevision();
        doThrow(new IllegalStateException("injected plan insertion failure")).when(store).publishPlanVersion(any(PlanVersion.class));
        assertThatThrownBy(()->admin.publish("actor",key(),"FREE",input)).hasMessageContaining("cannot be confirmed");
        assertThat(store.findPolicyVersions()).hasSize(2);assertThat(store.findPlanVersions()).hasSize(1);assertThat(store.readConfigurationRevision()).isEqualTo(revision);assertThat(mongoTemplate.findAll(AdminPublicationReceipt.class)).isEmpty();
    }
    @Test void unknownCommitRecoversOriginalReceiptWithoutSecondPublication(){
        AdminPublicationRequest input=input("TRIAL","NOW");
        doAnswer(invocation->{Object result=invocation.callRealMethod();com.mongodb.MongoException failure=new com.mongodb.MongoException("injected unknown commit");failure.addLabel("UnknownTransactionCommitResult");throw failure;}).when(transactions).execute(any());
        var accepted=admin.publish("actor",key(),"TRIAL",input);
        assertThat(accepted.created()).isFalse();assertThat(accepted.result().acceptedAt()).isEqualTo(START);
        assertThat(store.findPolicyVersions()).hasSize(3);assertThat(mongoTemplate.findAll(AdminPublicationReceipt.class)).hasSize(1);
    }
    @RepeatedTest(2) void concurrentDifferentKeysHaveOneWinnerAndSameKeyReturnsExactResult()throws Exception{
        AdminPublicationRequest input=input("TRIAL","NOW");
        List<Object> results=race(()->admin.publish("actor",key(),"TRIAL",input),()->admin.publish("actor",key(),"TRIAL",input));
        assertThat(results.stream().filter(AdminConfigurationService.Accepted.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(fit.iuh.engolearn.subscription.exception.SubscriptionRequestException.class::isInstance)).hasSize(1);
        String shared=key();AdminPublicationRequest next=input("TRIAL","NOW");
        List<Object> same=race(()->admin.publish("actor",shared,"TRIAL",next),()->admin.publish("actor",shared,"TRIAL",next));
        assertThat(same).allMatch(AdminConfigurationService.Accepted.class::isInstance);
        assertThat(((AdminConfigurationService.Accepted)same.get(0)).result()).isEqualTo(((AdminConfigurationService.Accepted)same.get(1)).result());
        assertThat(mongoTemplate.findAll(AdminPublicationReceipt.class)).hasSize(2);assertThat(store.findPolicyVersions()).hasSize(4);
    }
    List<Object> race(Callable<Object> first,Callable<Object> second)throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch latch=new CountDownLatch(1);
        try{List<Future<Object>> futures=new ArrayList<>();for(Callable<Object> call:List.of(first,second))futures.add(pool.submit(()->{latch.await();try{return call.call();}catch(RuntimeException ex){return ex;}}));latch.countDown();return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
    }
    @Test void previewHasNoWritesPendingDueIsCurrentAndMidnightIsRecomputed(){
        AdminPublicationRequest input=input("FREE","NEXT_LOCAL_MIDNIGHT");long revision=store.readConfigurationRevision();
        var preview=admin.preview("FREE",input);assertThat(store.readConfigurationRevision()).isEqualTo(revision);assertThat(store.findPolicyVersions()).hasSize(2);assertThat(mongoTemplate.findAll(AdminPublicationReceipt.class)).isEmpty();
        clock.at=preview.effectiveAt();var saved=admin.publish("actor",key(),"FREE",input);assertThat(saved.result().effectiveAt()).isEqualTo(preview.effectiveAt().plus(Duration.ofDays(1)));
        clock.at=saved.result().effectiveAt();var config=admin.configuration("FREE");assertThat(config.current().version()).isEqualTo(2);assertThat(config.pending()).isNull();assertThat(config.activationPending()).isTrue();
        assertThatThrownBy(()->admin.preview("FREE",new AdminPublicationRequest(config.revision(),input.effect(),input.content()))).hasMessageContaining("awaiting activation");
    }
    @Test void newTrialUsesNewBenefitsWhileExistingTrialAndSyntheticPaidSnapshotsRemainImmutable(){
        subscriptions.activateTrial("u",key());SubscriptionPeriod prior=store.findPeriodsNewestFirst("u").getFirst();
        store.createSubscriptionPeriod(SubscriptionModelMapper.subscriptionPeriod("synthetic-paid","paid","MONTHLY","ACTIVE",START,START.plus(Duration.ofDays(30)),SubscriptionModelMapper.toDto(prior.getSnapshot()),SubscriptionModelMapper.toDto(prior.getBenefits()),List.of()));
        ConfigurationContentRequest old=input("TRIAL","NOW").content();ConfigurationContentRequest changed=new ConfigurationContentRequest("New Trial",null,0,List.of(new PlanBenefitRequest("B","Changed","Changed description",null,0)),List.of(),old.quotaRules());
        admin.publish("actor",key(),"TRIAL",new AdminPublicationRequest(admin.catalog().revision(),new PublicationEffectRequest("NOW",null),changed));
        subscriptions.activateTrial("v",key());assertThat(store.findPeriodsNewestFirst("u").getFirst()).isEqualTo(prior);assertThat(store.findPeriodsNewestFirst("paid").getFirst().getSnapshot()).isEqualTo(prior.getSnapshot());
        assertThat(store.findPeriodsNewestFirst("v").getFirst().getBenefits().getDisplayName()).isEqualTo("New Trial");assertThat(store.findPeriodsNewestFirst("v").getFirst().getBenefits().getBenefits().getFirst().getDisplayValue()).isNull();
    }
    @Test void canonicalAcrossScopesRejectsChangesAndRemovalsAndMissingBaselineIsUnavailable(){
        AdminPublicationRequest input=input("TRIAL","NOW");ConfigurationContentRequest c=input.content();ConfigurationContentRequest bad=new ConfigurationContentRequest(c.displayName(),null,0,c.benefits(),c.billingOptions(),List.of(new QuotaRuleRequest("TEST","Test","ATTEMPT","WEEKLY",2L,0)));
        assertThatThrownBy(()->admin.preview("TRIAL",new AdminPublicationRequest(input.expectedRevision(),input.effect(),bad))).hasMessageContaining("unit and period");
        mongoTemplate.remove(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("scope").is("FREE")),PlanVersion.class);
        assertThatThrownBy(()->admin.configuration("FREE")).hasMessageContaining("cannot be confirmed");
    }
    @Test void premiumStartsFromExplicitContentNowOrAtAndHistoryIsAppendOnly(){
        assertThat(admin.configuration("PREMIUM").current()).isNull();
        ConfigurationContentRequest trial=input("TRIAL","NOW").content();ConfigurationContentRequest premium=new ConfigurationContentRequest("Premium","",4,List.of(new PlanBenefitRequest("B","Benefit","Description","visible",0)),List.of(new PlanBillingOptionRequest("MONTHLY",123L,"VND")),trial.quotaRules());
        var accepted=admin.publish("actor",key(),"PREMIUM",new AdminPublicationRequest(admin.catalog().revision(),new PublicationEffectRequest("AT",START.plusSeconds(2)),premium));
        assertThat(admin.configuration("PREMIUM").current()).isNull();assertThat(admin.configuration("PREMIUM").pending().version()).isEqualTo(1);
        clock.at=accepted.result().effectiveAt();assertThat(subscriptions.policy("PREMIUM",clock.at).getVersion()).isEqualTo(1);scheduler.activateDue();
        assertThat(admin.configuration("PREMIUM").current().content().description()).isEmpty();
        admin.publish("actor",key(),"PREMIUM",new AdminPublicationRequest(admin.catalog().revision(),new PublicationEffectRequest("NOW",null),premium));
        assertThat(admin.detail("PREMIUM",1).status()).isEqualTo("HISTORICAL");assertThat(admin.history("PREMIUM",1,null).nextBeforeVersion()).isEqualTo(2);
        assertThat(admin.history("PREMIUM",1,2L).items()).extracting(AdminConfigurationResponse.ConfigurationVersionResponse::version).containsExactly(1L);
        assertThat(admin.detail("PREMIUM",2).content()).isEqualTo(admin.normalize(new AdminPublicationRequest(0L,new PublicationEffectRequest("NOW",null),premium)).content());
    }
    @RepeatedTest(2) void canonicalIntroductionRaceAcrossScopesHasOneWinner()throws Exception{
        AdminPublicationRequest trial=input("TRIAL","NOW"),free=input("FREE","NEXT_LOCAL_MIDNIGHT");
        ConfigurationContentRequest tc=trial.content(),fc=free.content();
        List<QuotaRuleRequest> tr=new ArrayList<>(tc.quotaRules()),fr=new ArrayList<>(fc.quotaRules());tr.add(new QuotaRuleRequest("NEW_TYPE","New","COUNT","MONTHLY",3L,1));fr.add(new QuotaRuleRequest("NEW_TYPE","New","SECOND","DAILY",3L,1));
        AdminPublicationRequest t=new AdminPublicationRequest(trial.expectedRevision(),trial.effect(),new ConfigurationContentRequest(tc.displayName(),null,0,tc.benefits(),tc.billingOptions(),tr));
        AdminPublicationRequest f=new AdminPublicationRequest(free.expectedRevision(),free.effect(),new ConfigurationContentRequest(fc.displayName(),fc.description(),fc.displayOrder(),fc.benefits(),fc.billingOptions(),fr));
        List<Object> results=race(()->admin.publish("actor",key(),"TRIAL",t),()->admin.publish("actor",key(),"FREE",f));
        assertThat(results.stream().filter(AdminConfigurationService.Accepted.class::isInstance)).hasSize(1);assertThat(store.findPolicyVersions()).hasSize(3);assertThat(admin.catalog().quotaTypes().stream().filter(q->q.quotaType().equals("NEW_TYPE"))).hasSize(1);
        AdminPublicationRequest loser=((AdminConfigurationService.Accepted)results.stream().filter(AdminConfigurationService.Accepted.class::isInstance).findFirst().orElseThrow()).result().scope().equals("TRIAL")?f:t;
        String loserScope=loser==f?"FREE":"TRIAL";
        assertThatThrownBy(()->admin.preview(loserScope,new AdminPublicationRequest(admin.catalog().revision(),loser.effect(),loser.content()))).hasMessageContaining("unit and period");
    }
    ResponseEntity<String> http(String path,HttpMethod method,String fixture,String body){HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(fixture!=null)headers.set("X-Dev-User-Id",fixture);headers.set("Idempotency-Key",key());return restTemplate.exchange("http://localhost:"+port+"/api/v1/subscription/admin"+path,method,new HttpEntity<>(body,headers),String.class);}
    @Test void allHttpMethodsEnforceAdminBeforeReadingDataAndRejectForgedFieldsAndFractionalNumbers()throws Exception{
        Map<String,HttpMethod> paths=new LinkedHashMap<>();paths.put("/catalog",HttpMethod.GET);paths.put("/configurations/FREE",HttpMethod.GET);paths.put("/configurations/FREE/versions",HttpMethod.GET);paths.put("/configurations/FREE/versions/1",HttpMethod.GET);paths.put("/configurations/FREE/preview",HttpMethod.POST);paths.put("/configurations/FREE/publications",HttpMethod.POST);
        String body=mapper.writeValueAsString(input("FREE","NEXT_LOCAL_MIDNIGHT"));
        for(var endpoint:paths.entrySet())for(String role:Arrays.asList(null,"dev-user","dev-cm")){
            assertThatThrownBy(()->http(endpoint.getKey(),endpoint.getValue(),role,body)).isInstanceOfSatisfying(HttpClientErrorException.class,ex->{assertThat(ex.getStatusCode().value()).isEqualTo(role==null?401:403);assertThat(ex.getResponseHeaders().getFirst("Cache-Control")).isEqualTo("no-store");});
        }
        for(String path:List.of("/catalog","/configurations/FREE","/configurations/FREE/versions","/configurations/FREE/versions/1"))assertThat(http(path,HttpMethod.GET,"dev-admin",null).getStatusCode().value()).isEqualTo(200);
        assertThat(http("/configurations/FREE/preview",HttpMethod.POST,"dev-admin",body).getStatusCode().value()).isEqualTo(200);
        for(String malformed:List.of(body.replaceFirst("\\{","{\"accounts\":[],"),body.replace("\"limit\":2","\"limit\":2.5"),body.replace("\"expectedRevision\":1","\"expectedRevision\":1.5"),body.replace("\"displayOrder\":0","\"displayOrder\":0.5"),body.replace("\"description\":null,",""))){
            assertThatThrownBy(()->http("/configurations/FREE/preview",HttpMethod.POST,"dev-admin",malformed)).isInstanceOfSatisfying(HttpClientErrorException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(400));
        }
        String fractionalPrice=body.replace("\"billingOptions\":[]","\"billingOptions\":[{\"billingPeriod\":\"MONTHLY\",\"amount\":123.5,\"currency\":\"VND\"}]").replace("NEXT_LOCAL_MIDNIGHT","NOW");
        assertThatThrownBy(()->http("/configurations/PREMIUM/preview",HttpMethod.POST,"dev-admin",fractionalPrice)).isInstanceOfSatisfying(HttpClientErrorException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(400));
        var accepted=http("/configurations/FREE/publications",HttpMethod.POST,"dev-admin",body);assertThat(accepted.getStatusCode().value()).isEqualTo(201);
    }
}
