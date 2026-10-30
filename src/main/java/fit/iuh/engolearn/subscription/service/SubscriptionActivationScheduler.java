package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.subscription.contract.QuotaPolicyActivated;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRule;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionStore;
import java.time.Clock;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
@Service
public class SubscriptionActivationScheduler {
    private final SubscriptionStore store;private final SubscriptionTransactions transactions;private final Clock clock;private final ApplicationEventPublisher events;
    public SubscriptionActivationScheduler(SubscriptionStore store,SubscriptionTransactions transactions,Clock clock,ApplicationEventPublisher events) {this.store=store;this.transactions=transactions;this.clock=clock;this.events=events;}
    @Scheduled(fixedDelayString="${engolearn.subscription.activation-delay-ms:60000}")
    public void activateDue() {
        for(PolicyState observed:store.due(clock.instant())) transactions.execute(()->{
            PolicyState state=store.state(observed.scope());
            if(state.pendingVersionId()==null || clock.instant().isBefore(state.pendingEffectiveAt())) return null;
            if(!store.advanceConfiguration(store.configurationRevision())) throw new org.springframework.dao.OptimisticLockingFailureException("Configuration revision changed");
            PolicyVersion old=state.activeVersionId()==null?null:store.policy(state.activeVersionId()), next=store.policy(state.pendingVersionId());
            if(!store.activate(state)) return null;
            if("FREE".equals(state.scope())) {
                Map<String,QuotaRule> prior=new HashMap<>();for(QuotaRule r:old.snapshot().rules()) prior.put(r.quotaType(),r);
                Set<String> changed=new HashSet<>();
                for(QuotaRule r:next.snapshot().rules()) {QuotaRule previous=prior.get(r.quotaType());if(previous==null || previous.limit()!=r.limit() || !previous.unit().equals(r.unit()) || !previous.period().equals(r.period())) changed.add(r.quotaType());}
                QuotaPolicyActivated event=new QuotaPolicyActivated(state.scope()+":"+(state.activationSequence()+1),state.scope(),next.version(),state.activationSequence()+1,next.effectiveAt(),changed,next.snapshot());
                transactions.afterCommit(()->events.publishEvent(event));
            }
            return null;
        });
    }
}
