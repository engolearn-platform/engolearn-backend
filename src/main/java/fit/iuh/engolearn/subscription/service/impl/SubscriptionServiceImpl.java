package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.subscription.contract.*;
import fit.iuh.engolearn.subscription.contract.event.EntitlementChangedEvent;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService, EffectiveEntitlementProvider {
    private final SubscriptionRepository store;
    private final SubscriptionTransactions transactions;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public List<PlanCardResponse> plans() {
        try {
            policy("FREE", clock.instant());
            Map<String, PlanVersion> latest = new HashMap<>();
            for (PlanVersion p : store.findPublishedPlans(clock.instant()))
                latest.putIfAbsent(p.getPlan().getPlanCode().name(), p);
            return latest.values()
                         .stream()
                         .sorted(Comparator.comparingInt(PlanVersion::getDisplayOrder)
                                           .thenComparing(p -> p.getPlan().getPlanCode().name()))
                         .map(p -> SubscriptionModelMapper.sortedPlanCard(p.getPlan()))
                         .toList();
        } catch (RuntimeException ex) {
            throw new SubscriptionRequestException(503, "PLAN_CONFIGURATION_UNAVAILABLE",
                                                   "Published plan configuration cannot be confirmed");
        }
    }

    private SubscriptionAccount account(String userId) {
        SubscriptionAccount a = store.findAccount(userId);
        if (a == null || a.getProvenance() == null) throw new SubscriptionUnavailableException();
        return a;
    }

    private SubscriptionPeriod active(List<SubscriptionPeriod> periods, Instant at) {
        List<SubscriptionPeriod> active = periods.stream()
                                                 .filter(p -> "ACTIVE".equals(p.getState().name()) &&
                                                              !at.isBefore(p.getPeriodStartAt()) &&
                                                              at.isBefore(p.getPeriodEndAt()))
                                                 .toList();
        if (active.size() > 1) throw new SubscriptionUnavailableException();
        return active.isEmpty() ? null : active.getFirst();
    }

    public PolicyVersion policy(String scope, Instant at) {
        PolicyState s = store.findPolicyState(scope);
        if (s == null) throw new SubscriptionUnavailableException();
        String id = s.getPendingVersionId() != null &&
                    !at.isBefore(s.getPendingEffectiveAt()) ? s.getPendingVersionId() : s.getActiveVersionId();
        PolicyVersion p = store.findPolicyVersion(id);
        if (p == null || at.isBefore(p.getEffectiveAt())) throw new SubscriptionUnavailableException();
        return p;
    }

    @Override
    public EffectiveEntitlementDto resolveEffectiveEntitlement(String userId, Instant at) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return transactions.execute(() -> resolveEffectiveEntitlement(userId, at));
        try {
            SubscriptionAccount a = account(userId);
            SubscriptionPeriod p = active(store.findPeriodsNewestFirst(userId), at);
            if (p != null) return SubscriptionModelMapper.entitlement(userId, a.getEntitlementVersion() - 1,
                    p.getSnapshot(), 0, p.getPeriodStartAt(), p.getPeriodEndAt());
            PolicyVersion free = policy("FREE", at);
            PolicyState state = store.findPolicyState("FREE");
            boolean pending = Objects.equals(state.getPendingVersionId(), free.getId());
            Instant validUntil = state.getPendingEffectiveAt() != null && at.isBefore(state.getPendingEffectiveAt())
                    ? state.getPendingEffectiveAt() : null;
            return SubscriptionModelMapper.entitlement(userId, a.getEntitlementVersion(), "FREE", free.getId(), free.getVersion(), free.getSnapshot(),
                    state.getActivationSequence() + (pending ? 1 : 0), free.getEffectiveAt(), validUntil);
        } catch (SubscriptionUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SubscriptionUnavailableException(ex);
        }
    }

    public MySubscriptionSummaryResponse summary(String userId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return transactions.execute(() -> summary(userId));
        try {
            Instant at = clock.instant();
            SubscriptionAccount a = account(userId);
            List<SubscriptionPeriod> periods = store.findPeriodsNewestFirst(userId);
            SubscriptionPeriod p = active(periods, at);
            boolean paid = p != null && !"TRIAL".equals(p.getKind().name());
            String reason = paid ? "ACTIVE_PREMIUM" : a.isTrialUsed() ? "ALREADY_USED" : null;
            SubscriptionPeriod latest = p != null ? p : periods.isEmpty() ? null : periods.getFirst();
            return new MySubscriptionSummaryResponse(p == null ? "FREE" : "PREMIUM", p != null ? (paid ? "ACTIVE" : "TRIAL") :
                    latest == null ? "FREE" : "REFUNDED".equals(latest.getState().name()) ? "REFUNDED" : "EXPIRED",
                                             latest == null ? null : latest.getPeriodStartAt(),
                                             latest == null ? null : latest.getPeriodEndAt(), reason == null, reason,
                                             p == null ? SubscriptionModelMapper.toDto(
                                                     policy("FREE", at).getBenefits()) : SubscriptionModelMapper.toDto(
                                                     p.getBenefits()));
        } catch (SubscriptionUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SubscriptionUnavailableException(ex);
        }
    }

    public ActivationResult activateTrial(String userId, String key) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return activate(userId, key);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(() -> activate(userId, key));
            } catch (SubscriptionRequestException | SubscriptionUnavailableException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (!unknownCommit(ex) && retryable(ex)) {
                    if (attempt < 2) continue;
                    throw new SubscriptionUnavailableException(ex);
                }
                try {
                    TrialReceipt receipt = store.findTrialReceipt(userId, key);
                    if (receipt != null)
                        return new ActivationResult(SubscriptionModelMapper.toDto(receipt.getResult()), false);
                } catch (RuntimeException lookup) {
                    throw new SubscriptionUnavailableException(lookup);
                }
                throw new SubscriptionUnavailableException(ex);
            }
        }
        throw new SubscriptionUnavailableException();
    }

    private boolean unknownCommit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof com.mongodb.MongoException mongo &&
                mongo.hasErrorLabel("UnknownTransactionCommitResult")) return true;
        return false;
    }

    private boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException ||
                cause instanceof org.springframework.dao.OptimisticLockingFailureException) return true;
            if (cause instanceof com.mongodb.MongoException mongo &&
                (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode() == 112 ||
                 mongo.getCode() == 11000)) return true;
        }
        return false;
    }

    private ActivationResult activate(String userId, String key) {
        TrialReceipt receipt = store.findTrialReceipt(userId, key);
        if (receipt != null) {
            if (!"ACTIVATE_TRIAL:{}".equals(receipt.getFingerprint()))
                throw new SubscriptionRequestException(409, "IDEMPOTENCY_CONFLICT",
                                                       "The intent key was already used for another request");
            return new ActivationResult(SubscriptionModelMapper.toDto(receipt.getResult()), false);
        }
        Instant now = Instant.ofEpochMilli(clock.millis());
        SubscriptionAccount a = account(userId);
        SubscriptionPeriod p = active(store.findPeriodsNewestFirst(userId), now);
        if (p != null && !"TRIAL".equals(p.getKind().name()))
            throw new SubscriptionRequestException(409, "PREMIUM_ALREADY_ACTIVE", "Premium is already active");
        boolean created = p == null;
        if (created) {
            if (a.isTrialUsed()) throw new SubscriptionRequestException(409, "TRIAL_ALREADY_USED",
                                                                        "The one-time Trial has already been used");
            PolicyVersion config = policy("TRIAL", now);
            if (!store.markTrialUsed(a))
                throw new org.springframework.dao.OptimisticLockingFailureException("Subscription revision changed");
            p = store.createSubscriptionPeriod(
                    SubscriptionModelMapper.subscriptionPeriod(UUID.randomUUID().toString(), userId, "TRIAL", "ACTIVE",
                                                               now, now.plus(Duration.ofDays(7)), config));
        }
        TrialActivationResponse result = new TrialActivationResponse(p.getId(), p.getPeriodStartAt(), p.getPeriodEndAt(),
                                                     p.getSnapshot().getPolicySnapshotId());
        store.recordTrialActivation(
                SubscriptionModelMapper.trialReceipt(UUID.randomUUID().toString(), userId, "ACTIVATE_TRIAL", key,
                                                     "ACTIVATE_TRIAL:{}", result));
        if (created) {
            EffectiveEntitlementDto entitlement = resolveEffectiveEntitlement(userId, now);
            transactions.afterCommit(() -> events.publishEvent(new EntitlementChangedEvent(entitlement)));
        }
        return new ActivationResult(result, created);
    }
}
