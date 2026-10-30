package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.payment.contract.event.PaymentExpiredEvent;
import fit.iuh.engolearn.payment.infra.event.CheckoutMessageConsumer;
import fit.iuh.engolearn.payment.model.PaymentTransaction;
import fit.iuh.engolearn.payment.model.enums.PaymentStatus;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.CheckoutService;
import fit.iuh.engolearn.payment.service.PaymentOutcomeService;
import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.repository.QuotaRepository;
import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.dto.response.PurchaseResponse;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.infra.event.PaymentResultMessageConsumer;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionMessageRepository;
import fit.iuh.engolearn.subscription.service.*;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;


@EnabledIfDockerAvailable
@Import(PurchaseFlowIntegrationTest.TimeConfig.class)
@TestPropertySource(properties={"engolearn.messaging.enabled=false","engolearn.subscription.activation-delay-ms=3600000"})
class PurchaseFlowIntegrationTest extends AbstractMongoIntegrationTest {
    static final Instant START=Instant.parse("2027-01-31T03:00:00Z");
    static final String USER="68f8f3a9b9e4a12345678901";
    static final CreatePurchaseRequest MONTH=new CreatePurchaseRequest("PREMIUM","MONTHLY");
    static class MutableClock extends Clock {
        volatile Instant now=START;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock purchaseClock(){return new MutableClock();}}
    @Autowired MutableClock clock;
    @Autowired List<DurableMessageRepository> repositories;
    @Autowired PurchaseService purchases;
    @Autowired PurchaseRepository store;
    @Autowired SubscriptionPublicationService publication;
    @Autowired SubscriptionTransactions transactions;
    @Autowired DurableMessageDispatcher dispatcher;
    @Autowired PaymentTransactionRepository payments;
    @Autowired CheckoutService checkout;
    @Autowired CheckoutMessageConsumer checkoutConsumer;
    @Autowired PaymentMessageRepository paymentMessages;
    @Autowired MongoMessageTransactions messageTransactions;
    @Autowired ObjectMapper mapper;

    @BeforeEach void fixtures(){
        clock.now=START;repositories.forEach(DurableMessageRepository::ensureStorage);quotaStore.ensureIndexes();
        publication.publish(new PublicationManifestRequest("purchase-fixture","isolated-test",
                List.of(new PublicationManifestRequest.PlanRequest("premium-plan","PREMIUM",1,START.minusSeconds(1),true,0,
                        new PlanCardResponse("PREMIUM","Premium",null,List.of(),List.of(
                                new PlanBillingOptionResponse("MONTHLY",100000,"VND"),new PlanBillingOptionResponse("ANNUAL",1000000,"VND"))))),
                List.of(policy("FREE",2),policy("TRIAL",5),policy("PREMIUM",10)),
                List.of(new PublicationManifestRequest.AccountHistoryRequest(USER,false,"NEW_ACCOUNT"))));
    }
    PublicationManifestRequest.PolicyRequest policy(String scope,long limit){
        return new PublicationManifestRequest.PolicyRequest(scope+"1",scope,1,START.minusSeconds(1),
                new QuotaPolicySnapshotDto(scope+"1",scope,1,List.of(new QuotaRuleDto("TEST","Test",limit,"ATTEMPT","MONTHLY",0))),
                new CurrentBenefitsResponse(scope.equals("FREE")?"FREE":"PREMIUM",scope,scope+"1",
                        List.of(new PlanBenefitResponse("TEST","Test","Test benefit",null,0))));
    }
    String key(){return UUID.randomUUID().toString();}
    PurchaseResponse create(){return purchases.create(USER,key(),MONTH).purchase();}
    void drain(){for(int hop=0;hop<4;hop++)dispatcher.dispatch();}
    long count(String collection){return mongoTemplate.getCollection(collection).countDocuments();}
    void paymentEvent(String id,String type,String reference,Object event){
        messageTransactions.independent(()->{paymentMessages.append(new MessageEnvelopeDto(id,type,1,reference,
                "subscription.payment",clock.instant(),mapper.writeValueAsString(event)));return null;});
    }

    void historyFixture(String id,String owner,PurchaseStatus status,Instant created){
        store.insert(PremiumPurchase.builder().id(id).purchaseReference(id).userId(owner).idempotencyKey("fixture-"+id)
                .billingPeriod(BillingPeriod.MONTHLY).amountVnd(100000).currency("VND").status(status)
                .intentCreatedAt(created).expiresAt(created.minusSeconds(1)).checkoutUrl("https://checkout.example.test/expired")
                .refundReference(status==PurchaseStatus.REFUNDED?"refund-ref":null)
                .refundId(status==PurchaseStatus.REFUNDED?"refund-id":null)
                .refundStatus(status==PurchaseStatus.REFUNDED?PurchaseRefundStatus.SUCCEEDED:null).build());
        mongoTemplate.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id)),
                new org.springframework.data.mongodb.core.query.Update().set("created_at",created),PremiumPurchase.class);
    }
    @Test void historyIsOwnerScopedStableAcrossTiesAndConcurrentInsertion(){
        historyFixture("history-a",USER,PurchaseStatus.PAYMENT_PENDING,START);
        historyFixture("history-b",USER,PurchaseStatus.PAYMENT_FAILED,START);
        historyFixture("history-c",USER,PurchaseStatus.REFUNDED,START);
        historyFixture("foreign-z","another-user",PurchaseStatus.ACTIVE,START.plusSeconds(10));
        var first=purchases.history(USER,2,null);
        assertThat(first.items()).extracting(PurchaseResponse::purchaseReference).containsExactly("history-c","history-b");
        assertThat(first.items().getFirst().refundReference()).isEqualTo("refund-ref");
        assertThat(first.items().getFirst().refundStatus()).isEqualTo("SUCCEEDED");
        assertThat(first.nextCursor()).isNotBlank();
        historyFixture("history-new",USER,PurchaseStatus.PAYMENT_SETUP_PENDING,START.plusSeconds(1));
        var second=purchases.history(USER,2,first.nextCursor());
        assertThat(second.items()).extracting(PurchaseResponse::purchaseReference).containsExactly("history-a");
        assertThat(second.items().getFirst().checkoutUrl()).isNull();assertThat(second.nextCursor()).isNull();
        assertThat(purchases.history("another-user",20,null).items()).extracting(PurchaseResponse::purchaseReference)
                .containsExactly("foreign-z");
        assertThat(count("subscription_purchases")).isEqualTo(5);
    }
    @Test void historyHttpRejectsAnonymousBadBoundsAndBadCursor(){
        String url="http://localhost:"+port+"/api/v1/subscription/me/purchases";
        assertThatThrownBy(()->restTemplate.getForEntity(url,String.class)).isInstanceOfSatisfying(HttpClientErrorException.class,
                e->assertThat(e.getStatusCode().value()).isEqualTo(401));
        HttpHeaders headers=new HttpHeaders();headers.set("X-Dev-User-Id","dev-user");
        var empty=restTemplate.exchange(url,HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertThat(empty.getStatusCode().value()).isEqualTo(200);assertThat(empty.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(mapper.readTree(empty.getBody()).path("data").path("items").size()).isZero();
        for(String query:List.of("?limit=0","?limit=51","?limit=abc","?cursor=not-a-cursor"))
            assertThatThrownBy(()->restTemplate.exchange(url+query,HttpMethod.GET,new HttpEntity<>(headers),String.class))
                    .isInstanceOfSatisfying(HttpClientErrorException.class,e->{
                        assertThat(e.getStatusCode().value()).isEqualTo(400);
                        assertThat(e.getResponseBodyAsString()).contains("INVALID_REQUEST");});
    }

    @Test void sameIntentReplaysPinnedPriceAndDifferentSelectionConflicts(){
        String id=key();var original=purchases.create(USER,id,MONTH);
        PlanVersion plan=mongoTemplate.findById("premium-plan",PlanVersion.class);
        plan.getPlan().getBillingOptions().getFirst().setAmount(999999);mongoTemplate.save(plan);
        var replay=purchases.create(USER,id.toUpperCase(Locale.ROOT),MONTH);
        assertThat(replay.created()).isFalse();assertThat(replay.purchase().amountVnd()).isEqualTo(100000);
        assertThat(replay.purchase().purchaseReference()).isEqualTo(original.purchase().purchaseReference());
        assertThatThrownBy(()->purchases.create(USER,id,new CreatePurchaseRequest("PREMIUM","ANNUAL")))
                .isInstanceOfSatisfying(SubscriptionRequestException.class,e->assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(count("subscription_purchases")).isEqualTo(1);assertThat(count("subscription_outbox")).isEqualTo(1);
        assertThatThrownBy(()->purchases.detail("dev-cm",original.purchase().purchaseReference()))
                .isInstanceOfSatisfying(SubscriptionRequestException.class,e->assertThat(e.status()).isEqualTo(404));
    }
    @RepeatedTest(2) void concurrentDifferentKeysCreateOneBlockingPurchase()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{
            Callable<Boolean> action=()->{start.await(10,TimeUnit.SECONDS);try{purchases.create(USER,key(),MONTH);return true;}
                catch(SubscriptionRequestException e){assertThat(e.code()).isEqualTo("PURCHASE_PENDING");return false;}};
            Future<Boolean> first=pool.submit(action),second=pool.submit(action);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("subscription_purchases")).isEqualTo(1);assertThat(count("subscription_outbox")).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void rollbackLeavesNoIntentOrCommandAndExpiredNeverIssuedIntentUnlocks(){
        assertThatThrownBy(()->transactions.execute(()->{create();throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(count("subscription_purchases")).isZero();assertThat(count("subscription_outbox")).isZero();
        PurchaseResponse purchase=create();clock.now=purchase.expiresAt().plusSeconds(1);drain();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(purchases.create(USER,key(),MONTH).created()).isTrue();
    }
    @Test void checkoutPreparationAndAuthoritativeExpiryKeepUnknownLocked(){
        PurchaseResponse purchase=create();drain();PaymentTransaction payment=payments.findByPurchase(purchase.purchaseReference());
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("PAYMENT_SETUP_PENDING");
        checkout.prepared(payment.getId(),"https://checkout.example.test/session",purchase.expiresAt());drain();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).checkoutUrl()).isEqualTo("https://checkout.example.test/session");
        clock.now=purchase.expiresAt().plusSeconds(1);
        assertThat(purchases.detail(USER,purchase.purchaseReference()).checkoutUrl()).isNull();
        assertThatThrownBy(()->create()).isInstanceOfSatisfying(SubscriptionRequestException.class,e->assertThat(e.code()).isEqualTo("PURCHASE_PENDING"));
        String id=key();paymentEvent(id,"PAYMENT_EXPIRED",purchase.purchaseReference(),new PaymentExpiredEvent(id,1,clock.instant(),
                payment.getId(),purchase.purchaseReference(),USER,clock.instant()));drain();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(purchases.create(USER,key(),MONTH).created()).isTrue();
    }
    @Test void conflictingCommandCannotRejectOrUnlockLegitimatePurchase(){
        PurchaseResponse purchase=create();drain();String id=key();
        var command=new CreateCheckoutCommandDto(id,1,START,purchase.purchaseReference(),USER,100001,"VND",purchase.expiresAt());
        checkoutConsumer.consume(new MessageEnvelopeDto(id,"CREATE_CHECKOUT",1,purchase.purchaseReference(),"payment.checkout",START,
                mapper.writeValueAsString(command)));
        assertThat(count("payment_transactions")).isEqualTo(1);assertThat(count("payment_message_reviews")).isEqualTo(1);
        assertThat(store.findByReference(purchase.purchaseReference()).isBlocksNewPurchase()).isTrue();
    }
    @Test void httpAuthenticationStrictBodyReplayAndOwnership(){
        String url="http://localhost:"+port+"/api/v1/subscription/me/purchases";
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("Idempotency-Key",key());
        assertThatThrownBy(()->restTemplate.postForEntity(url,new HttpEntity<>("{\"planCode\":\"PREMIUM\",\"billingPeriod\":\"MONTHLY\"}",headers),String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(401));
        headers.set("X-Dev-User-Id","dev-user");
        assertThatThrownBy(()->restTemplate.postForEntity(url,new HttpEntity<>("{\"planCode\":\"PREMIUM\",\"billingPeriod\":\"MONTHLY\",\"amount\":1}",headers),String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
        String body="{\"planCode\":\"PREMIUM\",\"billingPeriod\":\"MONTHLY\"}";
        assertThat(restTemplate.postForEntity(url,new HttpEntity<>(body,headers),String.class).getStatusCode().value()).isEqualTo(201);
        assertThat(restTemplate.postForEntity(url,new HttpEntity<>(body,headers),String.class).getStatusCode().value()).isEqualTo(200);
        var purchase=store.findByIntent(USER,headers.getFirst("Idempotency-Key"));headers.set("X-Dev-User-Id","dev-cm");
        assertThatThrownBy(()->restTemplate.exchange(url+"/"+purchase.getPurchaseReference(),HttpMethod.GET,new HttpEntity<>(headers),String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
    @Autowired SubscriptionService subscriptions;
    @Autowired PaymentOutcomeService outcomes;
    @Autowired PaymentResultMessageConsumer consumer;
    @Autowired QuotaConsumption consumption;
    @Autowired QuotaAvailability availability;
    @Autowired QuotaRepository quotaStore;
    @MockitoSpyBean SubscriptionMessageRepository receipts;
    String paymentId(PurchaseResponse purchase){return payments.findByPurchase(purchase.purchaseReference()).getId();}
    PaymentSucceededEvent success(PurchaseResponse purchase,String id,Instant paid){
        return new PaymentSucceededEvent(id,1,clock.instant(),paymentId(purchase),purchase.purchaseReference(),USER,
                purchase.amountVnd(),"VND",paid);
    }
    MessageEnvelopeDto envelope(PaymentSucceededEvent event){
        return new MessageEnvelopeDto(event.eventId(),"PAYMENT_SUCCEEDED",1,event.purchaseReference(),"subscription.payment",
                event.occurredAt(),mapper.writeValueAsString(event));
    }
    @Test void trialConversionCalendarClampRetainsUsageAndLateFactsCannotRegress(){
        subscriptions.activateTrial(USER,key());
        consumption.consume(new UsageCommandDto(USER,"TEST",1,"TEST","usage","r",null));
        PurchaseResponse purchase=create();drain();clock.now=START.plusSeconds(60);Instant paid=clock.instant();
        outcomes.confirmSuccess(paymentId(purchase),purchase.amountVnd(),"VND",paid);drain();
        PurchaseResponse active=purchases.detail(USER,purchase.purchaseReference());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.periodEndAt()).isEqualTo(Instant.parse("2027-02-28T03:01:00Z"));
        SubscriptionPeriod trial=mongoTemplate.findAll(SubscriptionPeriod.class).stream().filter(p->p.getKind()==PeriodKind.TRIAL).findFirst().orElseThrow();
        assertThat(trial.getState()).isEqualTo(PeriodState.SUPERSEDED);assertThat(trial.getSupersededAt()).isEqualTo(paid);
        assertThat(mongoTemplate.findById(USER,SubscriptionAccount.class).isTrialUsed()).isTrue();
        assertThat(availability.checkAvailability(USER,"TEST").used()).isEqualTo(1);
        assertThat(availability.checkAvailability(USER,"TEST").limit()).isEqualTo(10);
        consumer.consume(envelope(success(purchase,key(),paid)));
        String late=key();paymentEvent(late,"CHECKOUT_READY",purchase.purchaseReference(),new CheckoutReadyEvent(late,1,clock.instant(),
                purchase.purchaseReference(),paymentId(purchase),"https://checkout.example.test/late",purchase.expiresAt()));
        outcomes.confirmFailure(paymentId(purchase),"LATE_FAILURE");drain();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("ACTIVE");
        assertThat(count("subscription_periods")).isEqualTo(2);
        assertThatThrownBy(()->create()).isInstanceOfSatisfying(SubscriptionRequestException.class,e->assertThat(e.code()).isEqualTo("PREMIUM_ALREADY_ACTIVE"));
    }
    @RepeatedTest(2) void concurrentSuccessFactsCreateOnePeriod()throws Exception{
        PurchaseResponse purchase=create();drain();
        MessageEnvelopeDto first=envelope(success(purchase,key(),clock.instant())),second=envelope(success(purchase,key(),clock.instant()));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{
            java.util.function.Function<MessageEnvelopeDto,Callable<Boolean>> action=message->()->{
                start.await(10,TimeUnit.SECONDS);
                try{consumer.consume(message);return true;}catch(DataAccessException | com.mongodb.MongoException contention){return false;}};
            Future<Boolean> a=pool.submit(action.apply(first)),b=pool.submit(action.apply(second));start.countDown();
            boolean aa=a.get(20,TimeUnit.SECONDS),bb=b.get(20,TimeUnit.SECONDS);assertThat(aa||bb).isTrue();
            consumer.consume(first);consumer.consume(second);
            assertThat(count("subscription_periods")).isEqualTo(1);
            assertThat(count("subscription_message_receipts")).isEqualTo(2);
        }finally{pool.shutdownNow();}
    }
    @Test void mismatchedSuccessGoesToReviewWithoutGrantingOrUnlocking(){
        PurchaseResponse purchase=create();drain();String id=key();
        var invalid=new PaymentSucceededEvent(id,1,clock.instant(),paymentId(purchase),purchase.purchaseReference(),USER,100001,"VND",clock.instant());
        consumer.consume(envelope(invalid));
        assertThat(count("subscription_periods")).isZero();
        assertThat(store.findByReference(purchase.purchaseReference()).isBlocksNewPurchase()).isTrue();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("REQUIRES_REVIEW");
        assertThat(count("subscription_message_reviews")).isEqualTo(1);
        outcomes.confirmSuccess(paymentId(purchase),100001,"VND",clock.instant());
        assertThat(payments.findById(paymentId(purchase)).getStatus()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
    }
    @Test void activationReceiptFailureRollsBackTrialPeriodPurchaseAndEntitlementOutbox(){
        subscriptions.activateTrial(USER,key());PurchaseResponse purchase=create();drain();
        outcomes.confirmSuccess(paymentId(purchase),purchase.amountVnd(),"VND",clock.instant());
        var delivery=paymentMessages.claim(Set.of("subscription.payment"),clock.instant(),clock.instant().plusSeconds(30));
        long outboxBefore=count("subscription_outbox");
        doThrow(new IllegalStateException("receipt failure")).when(receipts).recordReceipt(any(),any());
        assertThatThrownBy(()->consumer.consume(delivery.envelope())).isInstanceOf(IllegalStateException.class);
        assertThat(count("subscription_periods")).isEqualTo(1);
        assertThat(mongoTemplate.findAll(SubscriptionPeriod.class).getFirst().getState()).isEqualTo(PeriodState.ACTIVE);
        assertThat(store.findByReference(purchase.purchaseReference()).getPeriodId()).isNull();
        assertThat(count("subscription_outbox")).isEqualTo(outboxBefore);
        reset(receipts);consumer.consume(delivery.envelope());consumer.consume(delivery.envelope());
        assertThat(count("subscription_periods")).isEqualTo(2);
        assertThat(purchases.detail(USER,purchase.purchaseReference()).status()).isEqualTo("ACTIVE");
    }
    @Test void annualLeapDayAndDelayedSuccessDoNotExtendExpiredPremium(){
        clock.now=Instant.parse("2028-02-29T03:00:00Z");Instant paid=clock.instant();
        PurchaseResponse purchase=purchases.create(USER,key(),new CreatePurchaseRequest("PREMIUM","ANNUAL")).purchase();drain();
        outcomes.markUnknown(paymentId(purchase));assertThat(store.findByReference(purchase.purchaseReference()).isBlocksNewPurchase()).isTrue();
        clock.now=paid.plus(Duration.ofDays(400));outcomes.confirmSuccess(paymentId(purchase),purchase.amountVnd(),"VND",paid);drain();
        assertThat(purchases.detail(USER,purchase.purchaseReference()).periodEndAt()).isEqualTo(Instant.parse("2029-02-28T03:00:00Z"));
        assertThat(mongoTemplate.findAll(SubscriptionPeriod.class).getFirst().getState()).isEqualTo(PeriodState.EXPIRED);
        assertThat(subscriptions.summary(USER).tier()).isEqualTo("FREE");
    }
    @Test void lateSuccessAfterFailurePreservesNewPurchaseLockForReview(){
        PurchaseResponse first=create();drain();outcomes.confirmFailure(paymentId(first),"DECLINED");drain();
        assertThat(purchases.detail(USER,first.purchaseReference()).status()).isEqualTo("PAYMENT_FAILED");
        PurchaseResponse second=create();consumer.consume(envelope(success(first,key(),clock.instant())));
        assertThat(count("subscription_periods")).isZero();
        assertThat(purchases.detail(USER,first.purchaseReference()).status()).isEqualTo("REQUIRES_REVIEW");
        assertThat(store.findByReference(second.purchaseReference()).isBlocksNewPurchase()).isTrue();
        assertThat(store.findBlocking(USER).getPurchaseReference()).isEqualTo(second.purchaseReference());
        assertThat(count("subscription_message_reviews")).isEqualTo(1);
    }
}
