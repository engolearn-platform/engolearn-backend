package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest;
import fit.iuh.engolearn.subscription.dto.response.PurchaseResponse;
import fit.iuh.engolearn.subscription.dto.response.PurchaseHistoryResponse;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {
    private final PurchaseRepository purchases;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMessageRepository messages;
    private final SubscriptionTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Value("${engolearn.payment.checkout-lifetime:PT15M}") private Duration checkoutLifetime;

    public Creation create(String userId, String key, CreatePurchaseRequest request) {
        if (request == null || !"PREMIUM".equals(request.planCode()) ||
                request.billingPeriod() == null || !Set.of("MONTHLY", "ANNUAL").contains(request.billingPeriod()))
            throw error(400, "INVALID_REQUEST", "A Premium billing option is required");
        String intentKey;
        try {
            intentKey = UUID.fromString(key).toString();
            if (!intentKey.equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw error(400, "INVALID_REQUEST", "A UUID Idempotency-Key is required"); }
        for (int attempt = 0; attempt < 3; attempt++) {
            try { return transactions.execute(() -> createOwned(userId, intentKey, request)); }
            catch (SubscriptionRequestException | SubscriptionUnavailableException failure) { throw failure; }
            catch (RuntimeException failure) {
                PremiumPurchase accepted = purchases.findByIntent(userId, intentKey);
                if (accepted != null) return replay(accepted, request);
                if (attempt == 2 || !retryable(failure)) throw new SubscriptionUnavailableException(failure);
                java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(50L * (attempt + 1)).toNanos());
            }
        }
        throw new SubscriptionUnavailableException();
    }

    private Creation createOwned(String userId, String key, CreatePurchaseRequest request) {
        PremiumPurchase accepted = purchases.findByIntent(userId, key);
        if (accepted != null) return replay(accepted, request);
        Instant now = Instant.ofEpochMilli(clock.millis());
        SubscriptionAccount account = subscriptions.findAccount(userId);
        if (account == null || account.getProvenance() == null) throw new SubscriptionUnavailableException();
        if (!purchases.guardAccount(account)) throw new OptimisticLockingFailureException("Account changed");
        for (SubscriptionPeriod period : subscriptions.findPeriodsNewestFirst(userId))
            if (period.getState() == PeriodState.ACTIVE && period.getKind() != PeriodKind.TRIAL &&
                    !now.isBefore(period.getPeriodStartAt()) && now.isBefore(period.getPeriodEndAt()))
                throw error(409, "PREMIUM_ALREADY_ACTIVE", "Premium is already active");
        PremiumPurchase blocking = purchases.findBlocking(userId);
        if (blocking != null) throw error(409, "PURCHASE_PENDING", "Purchase is still unresolved: " + blocking.getPurchaseReference());
        PlanVersion plan = subscriptions.findPublishedPlans(now).stream()
                .filter(p -> p.getScope() == PolicyScope.PREMIUM && p.getPlan().getPlanCode() == PlanCode.PREMIUM)
                .findFirst().orElseThrow(() -> error(409, "OFFER_UNAVAILABLE", "Premium is not currently offered"));
        PolicyState state = subscriptions.findPolicyState("PREMIUM");
        if (state == null) throw new SubscriptionUnavailableException();
        String policyId = state.getPendingVersionId() != null && !now.isBefore(state.getPendingEffectiveAt())
                ? state.getPendingVersionId() : state.getActiveVersionId();
        PolicyVersion policy = subscriptions.findPolicyVersion(policyId);
        if (policy == null || policy.getVersion() != plan.getVersion() || now.isBefore(policy.getEffectiveAt()))
            throw new SubscriptionUnavailableException();
        var option = plan.getPlan().getBillingOptions().stream()
                .filter(o -> o.getBillingPeriod().name().equals(request.billingPeriod())).findFirst()
                .orElseThrow(() -> error(409, "OFFER_UNAVAILABLE", "Billing option is unavailable"));
        MessageValidation.money(option.getAmount(), option.getCurrency());
        if (checkoutLifetime.isNegative() || checkoutLifetime.isZero()) throw new SubscriptionUnavailableException();
        String reference = UUID.randomUUID().toString();
        PremiumPurchase purchase = PremiumPurchase.builder().id(reference).purchaseReference(reference).userId(userId)
                .planCode(PlanCode.PREMIUM).billingPeriod(BillingPeriod.valueOf(request.billingPeriod()))
                .amountVnd(option.getAmount()).currency(option.getCurrency()).planVersion(plan.getVersion())
                .policyVersion(policy.getVersion()).planSnapshot(plan.getPlan()).benefitsSnapshot(policy.getBenefits())
                .policySnapshot(policy.getSnapshot()).selectedBillingOption(option).expiresAt(now.plus(checkoutLifetime))
                .status(PurchaseStatus.PAYMENT_SETUP_PENDING).idempotencyKey(key).requestFingerprint(fingerprint(request))
                .blocksNewPurchase(true).intentCreatedAt(now).createdAt(now).build();
        purchases.insert(purchase);
        String messageId = UUID.randomUUID().toString();
        CreateCheckoutCommandDto command = new CreateCheckoutCommandDto(messageId, 1, now, reference, userId,
                purchase.getAmountVnd(), purchase.getCurrency(), purchase.getExpiresAt());
        messages.append(new MessageEnvelopeDto(messageId, "CREATE_CHECKOUT", 1, reference, "payment.checkout", now,
                mapper.writeValueAsString(command)));
        return new Creation(response(purchase), true);
    }

    private Creation replay(PremiumPurchase purchase, CreatePurchaseRequest request) {
        if (!fingerprint(request).equals(purchase.getRequestFingerprint()))
            throw error(409, "IDEMPOTENCY_CONFLICT", "Intent key was used for another purchase selection");
        return new Creation(response(purchase), false);
    }
    public PurchaseResponse detail(String userId, String reference) {
        PremiumPurchase purchase = purchases.findByReference(reference);
        if (purchase == null || !userId.equals(purchase.getUserId()))
            throw error(404, "PURCHASE_NOT_FOUND", "Purchase was not found");
        return response(purchase);
    }
    public PurchaseHistoryResponse history(String userId, int limit, String cursor) {
        if (limit < 1 || limit > 50) throw error(400, "INVALID_REQUEST", "History limit must be between 1 and 50");
        Instant before = null;
        String beforeId = null;
        if (cursor != null) {
            try {
                if (cursor.isBlank() || cursor.length() > 512) throw new IllegalArgumentException();
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = decoded.split(":", 2);
                if (parts.length != 2 || parts[1].isBlank() || parts[1].length() > 128) throw new IllegalArgumentException();
                before = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                beforeId = parts[1];
            } catch (RuntimeException invalid) { throw error(400, "INVALID_REQUEST", "Invalid history cursor"); }
        }
        List<PremiumPurchase> rows = purchases.history(userId, limit + 1, before, beforeId);
        boolean more = rows.size() > limit;
        List<PremiumPurchase> page = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            PremiumPurchase last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getCreatedAt().toEpochMilli() + ":" + last.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new PurchaseHistoryResponse(page.stream().map(this::response).toList(), next);
    }

    private PurchaseResponse response(PremiumPurchase purchase) {
        String url = purchase.getStatus() == PurchaseStatus.PAYMENT_PENDING && clock.instant().isBefore(purchase.getExpiresAt())
                ? purchase.getCheckoutUrl() : null;
        return new PurchaseResponse(purchase.getPurchaseReference(), purchase.getStatus().name(), purchase.getBillingPeriod().name(),
                purchase.getAmountVnd(), purchase.getCurrency(), purchase.getPaymentId(), url, purchase.getExpiresAt(),
                purchase.getPaidAt(), purchase.getPeriodId(), purchase.getIntentCreatedAt() == null ? purchase.getCreatedAt() : purchase.getIntentCreatedAt(),
                purchase.getFailureCode(), purchase.getPeriodEndAt(), purchase.getRefundReference(), purchase.getRefundId(),
                purchase.getRefundStatus() == null ? null : purchase.getRefundStatus().name());
    }
    private String fingerprint(CreatePurchaseRequest request) { return request.planCode() + ":" + request.billingPeriod(); }
    private SubscriptionRequestException error(int status, String code, String message) {
        return new SubscriptionRequestException(status, code, message);
    }
    private boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException || cause instanceof OptimisticLockingFailureException) return true;
            if (cause instanceof com.mongodb.MongoException mongo &&
                    (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode() == 112)) return true;
        }
        return false;
    }
}
