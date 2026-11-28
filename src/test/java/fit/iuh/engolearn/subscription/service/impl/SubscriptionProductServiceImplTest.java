package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBenefitRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBillingOptionRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.QuotaRuleRequest;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.SubscriptionProduct;
import fit.iuh.engolearn.subscription.model.SubscriptionProductPublicationReceipt;
import fit.iuh.engolearn.subscription.model.SubscriptionProductVersion;
import fit.iuh.engolearn.subscription.model.enums.ProductSaleStatus;
import fit.iuh.engolearn.subscription.model.enums.ProductVisibility;
import fit.iuh.engolearn.subscription.model.vo.ProductContentVo;
import fit.iuh.engolearn.subscription.model.vo.QuotaRuleVo;
import fit.iuh.engolearn.subscription.model.enums.QuotaPeriod;
import fit.iuh.engolearn.subscription.repository.SubscriptionProductRepository;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import fit.iuh.engolearn.subscription.service.SubscriptionTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionProductServiceImplTest {
    private static final String CODE = "DYNAMIC_PLAN";
    private static final String ACTOR = "admin";
    private static final String FIRST_KEY = "f8868d08-e8df-48ce-b035-b2e874215a90";
    private static final String SECOND_KEY = "d4efdf64-53b9-4dde-9707-b802ec10eb00";

    @Test
    void newDraftCanBePublishedForSaleButCannotBeBoughtBeforeFirstVersion() {
        SubscriptionProductRepository products = mock(SubscriptionProductRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionTransactions transactions = mock(SubscriptionTransactions.class);
        when(transactions.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(products.findProduct("DYNAMIC_NEW")).thenReturn(null);
        when(products.insertProduct(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.findVersions("DYNAMIC_NEW")).thenReturn(List.of());
        var service = new SubscriptionProductServiceImpl(products, subscriptions, transactions,
                Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneOffset.UTC), new ObjectMapper());

        var draft = service.create("DYNAMIC_NEW");

        assertThat(draft.currentVersion()).isNull();
        assertThat(draft.visibility()).isEqualTo("VISIBLE");
        assertThat(draft.saleStatus()).isEqualTo("ON_SALE");
        assertThat(draft.hasDraft()).isTrue();
    }

    @Test
    void savesAiCompanionDraftWithCanonicalDailyTokenQuota() {
        SubscriptionProductRepository products = mock(SubscriptionProductRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionTransactions transactions = mock(SubscriptionTransactions.class);
        when(transactions.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(subscriptions.findPolicyVersions()).thenReturn(List.of());

        AtomicReference<SubscriptionProduct> product = new AtomicReference<>(SubscriptionProduct.builder()
                .id(CODE).planCode(CODE).visibility(ProductVisibility.VISIBLE).saleStatus(ProductSaleStatus.ON_SALE)
                .archived(false).draftRevision(0).purchaseFence(0).build());
        when(products.findProduct(CODE)).thenAnswer(invocation -> product.get());
        when(products.findVersions(CODE)).thenReturn(List.of());
        when(products.saveDraft(eq(CODE), eq(0L), any())).thenAnswer(invocation -> {
            SubscriptionProduct current = product.get();
            current.setDraftContent(invocation.getArgument(2));
            current.setDraftRevision(1);
            return true;
        });
        SubscriptionProductServiceImpl service = new SubscriptionProductServiceImpl(products, subscriptions,
                transactions, Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneOffset.UTC), new ObjectMapper());

        service.saveDraft(CODE, new SaveSubscriptionProductDraftRequest(0L, aiCompanionRequestContent()));

        var savedRule = product.get().getDraftContent().getQuotaRules().getFirst();
        assertThat(product.get().getDraftContent().getBenefits()).extracting("code").containsExactly("AI_COMPANION");
        assertThat(savedRule.getQuotaType()).isEqualTo("AI_COMPANION_TOKEN_USAGE");
        assertThat(savedRule.getUnit()).isEqualTo("TOKEN");
        assertThat(savedRule.getPeriod().name()).isEqualTo("DAILY");
        assertThat(SubscriptionProductRegistry.service("AI_COMPANION").quotaType())
                .isEqualTo("AI_COMPANION_TOKEN_USAGE");
    }

    @Test
    void publishedDraftCannotBePublishedAgainAndReceiptReplayKeepsAcceptedVersion() {
        SubscriptionProductRepository products = mock(SubscriptionProductRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionTransactions transactions = mock(SubscriptionTransactions.class);
        when(transactions.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        AtomicReference<SubscriptionProduct> product = new AtomicReference<>(SubscriptionProduct.builder()
                .id(CODE).planCode(CODE).visibility(ProductVisibility.VISIBLE).saleStatus(ProductSaleStatus.ON_SALE)
                .archived(false).draftRevision(0).draftContent(content("Plan one")).purchaseFence(0).build());
        List<SubscriptionProductVersion> versions = new ArrayList<>();
        Map<String, SubscriptionProductPublicationReceipt> receipts = new HashMap<>();
        when(products.findProduct(CODE)).thenAnswer(invocation -> product.get());
        when(products.findVersion(eq(CODE), anyLong())).thenAnswer(invocation -> {
            long requested = invocation.getArgument(1);
            return versions.stream().filter(value -> value.getVersion() == requested).findFirst().orElse(null);
        });
        when(products.findVersions(CODE)).thenAnswer(invocation -> versions.stream()
                .sorted(Comparator.comparingLong(SubscriptionProductVersion::getVersion).reversed()).toList());
        when(products.findPublicationReceipt(eq(ACTOR), anyString())).thenAnswer(invocation -> receipts.get(invocation.getArgument(1)));
        when(products.publish(eq(CODE), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            SubscriptionProduct current = product.get();
            Long expectedVersion = invocation.getArgument(1);
            long expectedRevision = invocation.getArgument(2);
            long nextVersion = invocation.getArgument(3);
            if (!Objects.equals(current.getCurrentVersion(), expectedVersion) || current.getDraftRevision() != expectedRevision
                    || current.isArchived()) return false;
            current.setCurrentVersion(nextVersion);
            current.setDraftContent(null);
            current.setDraftRevision(expectedRevision + 1);
            current.setPurchaseFence(current.getPurchaseFence() + 1);
            return true;
        });
        when(products.insertVersion(any())).thenAnswer(invocation -> {
            SubscriptionProductVersion version = invocation.getArgument(0);
            versions.add(version);
            return version;
        });
        when(products.insertPublicationReceipt(any())).thenAnswer(invocation -> {
            SubscriptionProductPublicationReceipt receipt = invocation.getArgument(0);
            receipts.put(receipt.getIdempotencyKey(), receipt);
            return receipt;
        });
        when(products.saveDraft(eq(CODE), anyLong(), any())).thenAnswer(invocation -> {
            SubscriptionProduct current = product.get();
            long expectedRevision = invocation.getArgument(1);
            if (current.getDraftRevision() != expectedRevision) return false;
            current.setDraftContent(invocation.getArgument(2));
            current.setDraftRevision(expectedRevision + 1);
            return true;
        });

        SubscriptionProductServiceImpl service = new SubscriptionProductServiceImpl(products, subscriptions,
                transactions, Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneOffset.UTC), new ObjectMapper());

        var first = service.publish(ACTOR, FIRST_KEY, CODE, new PublishSubscriptionProductRequest(0L));
        assertThat(first.currentVersion()).isEqualTo(1L);
        assertThat(first.draft().content()).isNull();
        assertThat(first.draftRevision()).isEqualTo(1L);
        assertThat(first.hasDraft()).isFalse();
        assertThat(first.publishedVersion().version()).isEqualTo(1L);

        assertThatThrownBy(() -> service.publish(ACTOR, SECOND_KEY, CODE, new PublishSubscriptionProductRequest(1L)))
                .isInstanceOf(SubscriptionRequestException.class)
                .extracting(error -> ((SubscriptionRequestException) error).code()).isEqualTo("INVALID_REQUEST");
        assertThat(versions).hasSize(1);

        assertThat(service.saveDraft(CODE, new SaveSubscriptionProductDraftRequest(1L, requestContent("Plan two")))
                .draftRevision()).isEqualTo(2L);
        var second = service.publish(ACTOR, SECOND_KEY, CODE, new PublishSubscriptionProductRequest(2L));
        assertThat(second.currentVersion()).isEqualTo(2L);

        var replay = service.publish(ACTOR, FIRST_KEY, CODE, new PublishSubscriptionProductRequest(0L));
        assertThat(replay.currentVersion()).isEqualTo(2L);
        assertThat(replay.publishedVersion().version()).isEqualTo(1L);
        assertThat(replay.publishedVersion().content().displayName()).isEqualTo("Plan one");
        assertThat(versions).hasSize(2);
    }

    @Test
    void publicPlanProjectsOnlyThePublishedDailySecondSpeakingRule() {
        SubscriptionProductRepository products = mock(SubscriptionProductRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionTransactions transactions = mock(SubscriptionTransactions.class);
        when(transactions.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        var product = SubscriptionProduct.builder().planCode(CODE).visibility(ProductVisibility.VISIBLE)
                .saleStatus(ProductSaleStatus.ON_SALE).currentVersion(3L).archived(false).build();
        var content = ProductContentVo.builder().displayName("Speaking Plus").description("").displayOrder(1)
                .benefits(List.of()).billingOptions(List.of()).quotaRules(List.of(
                        QuotaRuleVo.builder().quotaType("SPEAKING_DURATION").unit("SECOND")
                                .period(QuotaPeriod.DAILY).limit(7200).build(),
                        QuotaRuleVo.builder().quotaType("OTHER").unit("SECOND")
                                .period(QuotaPeriod.DAILY).limit(9).build())).build();
        when(products.findProduct(CODE)).thenReturn(product);
        when(products.findVersion(CODE, 3L)).thenReturn(SubscriptionProductVersion.builder()
                .planCode(CODE).version(3L).content(content).build());
        var service = new SubscriptionProductServiceImpl(products, subscriptions, transactions,
                Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneOffset.UTC), new ObjectMapper());

        var card = service.publicPlan(CODE);

        assertThat(card.speakingDurationDailyLimitSeconds()).isEqualTo(7200L);
        assertThat(new fit.iuh.engolearn.subscription.dto.response.PlanCardResponse(CODE, "Plan", "", List.of(),
                List.of(), 1L, "ON_SALE").speakingDurationDailyLimitSeconds()).isNull();
    }

    private ProductContentVo content(String name) {
        return ProductContentVo.builder().displayName(name).description("").displayOrder(1)
                .benefit(fit.iuh.engolearn.subscription.model.vo.PlanBenefitVo.builder()
                        .code("ASSESSMENT_FULL_ANALYSIS").title("Xem phân tích đầy đủ").description("")
                        .displayValue("Có").sortOrder(0).build())
                .billingOption(fit.iuh.engolearn.subscription.model.vo.PlanBillingOptionVo.builder()
                        .billingPeriod(fit.iuh.engolearn.subscription.model.enums.BillingPeriod.MONTHLY)
                        .amount(10000).currency("VND").build())
                .quotaRules(List.of()).build();
    }

    private SubscriptionProductContentRequest requestContent(String name) {
        return new SubscriptionProductContentRequest(name, "", 1,
                List.of(new PlanBenefitRequest("ASSESSMENT_FULL_ANALYSIS", "Xem phân tích đầy đủ", "", "Có", 0)),
                List.of(new PlanBillingOptionRequest("MONTHLY", 10000L, "VND")), List.<QuotaRuleRequest>of());
    }

    private SubscriptionProductContentRequest aiCompanionRequestContent() {
        return new SubscriptionProductContentRequest("AI Companion plan", "", 1,
                List.of(new PlanBenefitRequest("AI_COMPANION", "Trò chuyện với AI mỗi ngày", "", "Có", 0)),
                List.of(new PlanBillingOptionRequest("MONTHLY", 10000L, "VND")),
                List.of(new QuotaRuleRequest("AI_COMPANION_TOKEN_USAGE", "AI Companion tokens",
                        "TOKEN", "DAILY", 1L, 0)));
    }
}
