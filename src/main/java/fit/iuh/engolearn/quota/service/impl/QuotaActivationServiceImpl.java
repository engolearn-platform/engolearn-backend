package fit.iuh.engolearn.quota.service.impl;

import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import fit.iuh.engolearn.quota.model.*;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.repository.QuotaRepository;
import fit.iuh.engolearn.quota.service.*;
import fit.iuh.engolearn.subscription.contract.event.QuotaPolicyActivatedEvent;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlementDto;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRuleDto;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuotaActivationServiceImpl implements QuotaActivationService {
    private final QuotaRepository store;
    private final QuotaEntitlements entitlements;
    private final QuotaTransactions transactions;
    private final Clock clock;

    public void onPolicyActivated(QuotaPolicyActivatedEvent activation) {
        transactions.event(() -> { apply(activation); return true; });
    }

    private void apply(QuotaPolicyActivatedEvent activation) {
        Instant at = clock.instant();
        if (activation.activationId() == null || activation.activationId().isBlank() || activation.activationSequence() < 0
                || activation.effectiveAt() == null || activation.effectiveAt().isAfter(at)
                || !activation.policyScope().equals(activation.policySnapshot().policyScope())
                || activation.policyVersion() != activation.policySnapshot().policyVersion()) throw new QuotaUnavailableException();
        QuotaActivationReceipt head = store.findActivationHead(activation.policyScope());
        if (head != null && head.getActivationSequence() >= activation.activationSequence()) return;
        if (head == null) {
            store.insertActivationRecord(QuotaActivationReceipt.builder().kind(ActivationKind.SCOPE_ORDER)
                    .policyScope(PolicyScope.valueOf(activation.policyScope())).activationSequence(activation.activationSequence()).build());
        } else if (!store.advanceActivationHead(activation.policyScope(), head.getActivationSequence(), activation.activationSequence())) {
            throw new OptimisticLockingFailureException("Activation order changed");
        }
        entitlements.saveSnapshot(activation.policySnapshot());
        if ("FREE".equals(activation.policyScope())) reset(activation, at);
        for (String type : activation.changedQuotaTypes()) {
            if (activation.policySnapshot().rules().stream().noneMatch(rule -> rule.quotaType().equals(type))) throw new QuotaUnavailableException();
            store.insertActivationRecord(QuotaActivationReceipt.builder().kind(ActivationKind.RECEIPT)
                    .activationId(activation.activationId()).quotaType(type).policyScope(PolicyScope.valueOf(activation.policyScope()))
                    .policyVersion(activation.policyVersion()).activationSequence(activation.activationSequence())
                    .effectiveAt(activation.effectiveAt()).completedAt(at).state(ActivationState.COMPLETED).build());
        }
    }

    private void reset(QuotaPolicyActivatedEvent activation, Instant at) {
        Map<String, EffectiveEntitlementDto> users = new HashMap<>();
        for (QuotaUsageCounter counter : store.findCountersForTypes(activation.changedQuotaTypes())) {
            String userId = counter.getUserId();
            EffectiveEntitlementDto entitlement = users.computeIfAbsent(userId, key -> entitlements.authoritative(key, at));
            if (!"FREE".equals(entitlement.policyScope()) || entitlement.policyVersion() != activation.policyVersion()
                    || !entitlement.policySnapshotId().equals(activation.policySnapshot().policySnapshotId())) continue;
            QuotaRuleDto rule = entitlements.rule(entitlement, counter.getQuotaType());
            CounterView view = CounterView.from(counter, rule.unit(), rule.period(), at);
            if (!view.start().equals(counter.getCalendarWindowStartAt()) || view.start().isAfter(activation.effectiveAt())) continue;
            if (counter.getLastAppliedActivationSequence() != null
                    && counter.getLastAppliedActivationSequence() >= activation.activationSequence()) continue;
            long retained = 0;
            for (QuotaUsageLedger ledger : store.findUsageRecordedBetween(userId, rule.quotaType(), activation.effectiveAt(), view.end()))
                retained = Math.addExact(retained, ledger.getAmount());
            String allowance = view.allowanceStart().equals(activation.effectiveAt()) ? view.allowanceId() : UUID.randomUUID().toString();
            boolean updated = store.resetCounter(counter, view.start(), view.allowanceId(), retained, allowance,
                    activation.effectiveAt(), activation.activationSequence(), activation.activationId(), at);
            if (!updated) throw new OptimisticLockingFailureException("Counter changed during activation");
        }
    }
}
