package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.contract.*;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService implements EffectiveEntitlementProvider {
    private final SubscriptionStore store;
    private final SubscriptionTransactions transactions;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public SubscriptionService(SubscriptionStore store, SubscriptionTransactions transactions, Clock clock, ApplicationEventPublisher events) {
        this.store=store; this.transactions=transactions; this.clock=clock; this.events=events;
    }
    public List<PlanCard> plans() {
        try {
            policy("FREE",clock.instant());
            Map<String,PlanVersion> latest=new HashMap<>();
            for(PlanVersion p:store.plans(clock.instant())) latest.putIfAbsent(p.plan().planCode(),p);
            return latest.values().stream().sorted(Comparator.comparingInt(PlanVersion::displayOrder).thenComparing(p->p.plan().planCode()))
                .map(p->new PlanCard(p.plan().planCode(),p.plan().displayName(),p.plan().description(),
                    p.plan().benefits().stream().sorted(Comparator.comparingInt(PlanBenefit::sortOrder).thenComparing(PlanBenefit::code)).toList(),p.plan().billingOptions())).toList();
        } catch(RuntimeException ex) { throw new SubscriptionRequestException(503,"PLAN_CONFIGURATION_UNAVAILABLE","Published plan configuration cannot be confirmed"); }
    }
    private SubscriptionAccount account(String userId) {
        SubscriptionAccount a=store.account(userId);
        if(a==null || a.provenance()==null || a.provenance().isBlank()) throw new SubscriptionUnavailableException();
        return a;
    }
    private SubscriptionPeriod active(List<SubscriptionPeriod> periods, Instant at) {
        List<SubscriptionPeriod> active=periods.stream().filter(p->"ACTIVE".equals(p.state()) && !at.isBefore(p.periodStartAt()) && at.isBefore(p.periodEndAt())).toList();
        if(active.size()>1) throw new SubscriptionUnavailableException();
        return active.isEmpty()?null:active.getFirst();
    }
    public PolicyVersion policy(String scope, Instant at) {
        PolicyState s=store.state(scope);
        if(s==null) throw new SubscriptionUnavailableException();
        String id=s.pendingVersionId()!=null && !at.isBefore(s.pendingEffectiveAt())?s.pendingVersionId():s.activeVersionId();
        PolicyVersion p=store.policy(id);
        if(p==null || at.isBefore(p.effectiveAt())) throw new SubscriptionUnavailableException();
        return p;
    }
    @Override public EffectiveEntitlement resolveEffectiveEntitlement(String userId, Instant at) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return transactions.execute(()->resolveEffectiveEntitlement(userId,at));
        try {
            SubscriptionAccount a=account(userId);
            SubscriptionPeriod p=active(store.periods(userId),at);
            if(p!=null) return new EffectiveEntitlement(userId,a.entitlementVersion()-1,p.snapshot().policyScope(),p.snapshot().policySnapshotId(),p.snapshot().policyVersion(),0,p.periodStartAt(),p.periodEndAt(),p.snapshot());
            PolicyVersion free=policy("FREE",at);
            PolicyState state=store.state("FREE");
            boolean pending=Objects.equals(state.pendingVersionId(),free.id());
            return new EffectiveEntitlement(userId,a.entitlementVersion(),"FREE",free.id(),free.version(),state.activationSequence()+(pending?1:0),free.effectiveAt(),
                state.pendingEffectiveAt()!=null && at.isBefore(state.pendingEffectiveAt())?state.pendingEffectiveAt():null,free.snapshot());
        } catch(SubscriptionUnavailableException ex) { throw ex; }
        catch(RuntimeException ex) { throw new SubscriptionUnavailableException(ex); }
    }
    public MySubscriptionSummary summary(String userId) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return transactions.execute(()->summary(userId));
        try {
            Instant at=clock.instant(); SubscriptionAccount a=account(userId); List<SubscriptionPeriod> periods=store.periods(userId);
            SubscriptionPeriod p=active(periods,at); boolean paid=p!=null && !"TRIAL".equals(p.kind());
            String reason=paid?"ACTIVE_PREMIUM":a.trialUsed()?"ALREADY_USED":null;
            SubscriptionPeriod latest=p!=null?p:periods.isEmpty()?null:periods.getFirst();
            return new MySubscriptionSummary(p==null?"FREE":"PREMIUM",p!=null?(paid?"ACTIVE":"TRIAL"):latest==null?"FREE":"REFUNDED".equals(latest.state())?"REFUNDED":"EXPIRED",
                latest==null?null:latest.periodStartAt(),latest==null?null:latest.periodEndAt(),reason==null,reason,p==null?policy("FREE",at).benefits():p.benefits());
        } catch(SubscriptionUnavailableException ex) { throw ex; } catch(RuntimeException ex) { throw new SubscriptionUnavailableException(ex); }
    }
    public record ActivationResult(TrialActivation activation, boolean created) {}
    public ActivationResult activateTrial(String userId,String key) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) return activate(userId,key);
        for(int attempt=0;attempt<3;attempt++) {
            try { return transactions.execute(()->activate(userId,key)); }
            catch(SubscriptionRequestException | SubscriptionUnavailableException ex) { throw ex; }
            catch(RuntimeException ex) {
                if(!unknownCommit(ex) && retryable(ex)) {if(attempt<2) continue;throw new SubscriptionUnavailableException(ex);}
                try { TrialReceipt receipt=store.receipt(userId,key); if(receipt!=null) return new ActivationResult(receipt.result(),false); }
                catch(RuntimeException lookup) { throw new SubscriptionUnavailableException(lookup); }
                throw new SubscriptionUnavailableException(ex);
            }
        }
        throw new SubscriptionUnavailableException();
    }
    private boolean unknownCommit(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof com.mongodb.MongoException mongo && mongo.hasErrorLabel("UnknownTransactionCommitResult")) return true;
        return false;
    }
    private boolean retryable(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if(cause instanceof DuplicateKeyException || cause instanceof org.springframework.dao.OptimisticLockingFailureException) return true;
            if(cause instanceof com.mongodb.MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode()==112 || mongo.getCode()==11000)) return true;
        }
        return false;
    }
    private ActivationResult activate(String userId,String key) {
        TrialReceipt receipt=store.receipt(userId,key);
        if(receipt!=null) {
            if(!"ACTIVATE_TRIAL:{}".equals(receipt.fingerprint())) throw new SubscriptionRequestException(409,"IDEMPOTENCY_CONFLICT","The intent key was already used for another request");
            return new ActivationResult(receipt.result(),false);
        }
        Instant now=Instant.ofEpochMilli(clock.millis()); SubscriptionAccount a=account(userId); SubscriptionPeriod p=active(store.periods(userId),now);
        if(p!=null && !"TRIAL".equals(p.kind())) throw new SubscriptionRequestException(409,"PREMIUM_ALREADY_ACTIVE","Premium is already active");
        boolean created=p==null;
        if(created) {
            if(a.trialUsed()) throw new SubscriptionRequestException(409,"TRIAL_ALREADY_USED","The one-time Trial has already been used");
            PolicyVersion config=policy("TRIAL",now);
            if(!store.markTrial(a)) throw new org.springframework.dao.OptimisticLockingFailureException("Subscription revision changed");
            p=store.insert(new SubscriptionPeriod(UUID.randomUUID().toString(),userId,"TRIAL","ACTIVE",now,now.plus(Duration.ofDays(7)),config.snapshot(),config.benefits(),List.of()));
        }
        TrialActivation result=new TrialActivation(p.id(),p.periodStartAt(),p.periodEndAt(),p.snapshot().policySnapshotId());
        store.insert(new TrialReceipt(UUID.randomUUID().toString(),userId,"ACTIVATE_TRIAL",key,"ACTIVATE_TRIAL:{}",result));
        if(created) { EffectiveEntitlement entitlement=resolveEffectiveEntitlement(userId,now); transactions.afterCommit(()->events.publishEvent(new EntitlementChanged(entitlement))); }
        return new ActivationResult(result,created);
    }
}
