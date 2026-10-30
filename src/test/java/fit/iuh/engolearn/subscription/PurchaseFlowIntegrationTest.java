package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.payment.contract.event.PaymentExpiredEvent;
import fit.iuh.engolearn.payment.infra.event.CheckoutMessageConsumer;
import fit.iuh.engolearn.payment.model.PaymentTransaction;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.CheckoutService;
import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.*;
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
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

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
        clock.now=START;repositories.forEach(DurableMessageRepository::ensureStorage);
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
}
