package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.service.*;
import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.subscription.contract.event.QuotaPolicyActivatedEvent;
import fit.iuh.engolearn.subscription.model.vo.QuotaRuleVo;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;

import java.time.Clock;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionActivationServiceImpl implements SubscriptionActivationService {
    private final SubscriptionRepository store;
    private final SubscriptionTransactions transactions;
    private final Clock clock;
    private final SubscriptionMessagePublisher messages;

    public void activateDue() {
        for (PolicyState observed : store.findDuePolicyStates(clock.instant()))
            transactions.execute(() -> {
                PolicyState state = store.findPolicyState(observed.getId());
                if (state.getPendingVersionId() == null || clock.instant().isBefore(state.getPendingEffectiveAt()))
                    return null;
                if (!store.advanceConfigurationRevision(store.readConfigurationRevision()))
                    throw new org.springframework.dao.OptimisticLockingFailureException(
                            "Configuration revision changed");
                PolicyVersion old =
                        state.getActiveVersionId() == null ? null : store.findPolicyVersion(state.getActiveVersionId()),
                        next = store.findPolicyVersion(state.getPendingVersionId());
                if (!store.activatePendingPolicy(state)) return null;
                if ("FREE".equals(state.getId())) {
                    Map<String, QuotaRuleVo> prior = new HashMap<>();
                    for (QuotaRuleVo r : old.getSnapshot().getRules())
                        prior.put(r.getQuotaType(), r);
                    Set<String> changed = new HashSet<>();
                    for (QuotaRuleVo r : next.getSnapshot().getRules()) {
                        QuotaRuleVo previous = prior.get(r.getQuotaType());
                        if (previous == null || previous.getLimit() != r.getLimit() || !previous.getUnit().equals(r.getUnit()) ||
                            !previous.getPeriod().equals(r.getPeriod())) changed.add(r.getQuotaType());
                    }
                    QuotaPolicyActivatedEvent event =
                            new QuotaPolicyActivatedEvent(state.getId() + ":" + (state.getActivationSequence() + 1),
                                                     state.getId(), next.getVersion(),
                                                     state.getActivationSequence() + 1, next.getEffectiveAt(), changed,
                                                     SubscriptionModelMapper.toDto(next.getSnapshot()));
                    messages.policyActivated(event);
                }
                return null;
            });
    }
}
