package fit.iuh.engolearn.quota.service;

import fit.iuh.engolearn.quota.contract.QuotaUnavailableException;
import fit.iuh.engolearn.quota.repository.QuotaStore;
import fit.iuh.engolearn.subscription.contract.QuotaPolicyActivated;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlement;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRule;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static fit.iuh.engolearn.quota.repository.QuotaStore.*;
import static fit.iuh.engolearn.quota.service.QuotaEntitlements.*;

@Service
public class QuotaActivationHandler {
    private final QuotaStore store;
    private final QuotaEntitlements entitlements;
    private final QuotaTransactions transactions;
    private final Clock clock;

    public QuotaActivationHandler(QuotaStore store, QuotaEntitlements entitlements, QuotaTransactions transactions, Clock clock) {
        this.store = store;
        this.entitlements = entitlements;
        this.transactions = transactions;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyActivated(QuotaPolicyActivated activation) {
        transactions.event(() -> { apply(activation); return true; });
    }

    private void apply(QuotaPolicyActivated activation) {
        Instant at = clock.instant();
        if (activation.activationId() == null || activation.activationId().isBlank() || activation.activationSequence() < 0
                || activation.effectiveAt() == null || activation.effectiveAt().isAfter(at)
                || !activation.policyScope().equals(activation.policySnapshot().policyScope())
                || activation.policyVersion() != activation.policySnapshot().policyVersion()) throw new QuotaUnavailableException();
        Criteria scope = Criteria.where("kind").is("SCOPE_ORDER").and("policyScope").is(activation.policyScope());
        Document head = store.one(RECEIPTS, scope);
        if (head != null && number(head, "activationSequence") >= activation.activationSequence()) return;
        if (head == null) {
            store.insert(RECEIPTS, new Document("kind", "SCOPE_ORDER").append("policyScope", activation.policyScope())
                    .append("activationSequence", activation.activationSequence()));
        } else if (!store.update(RECEIPTS, scope.and("activationSequence").is(number(head, "activationSequence")),
                new Update().set("activationSequence", activation.activationSequence()))) {
            throw new OptimisticLockingFailureException("Activation order changed");
        }
        entitlements.saveSnapshot(activation.policySnapshot());
        if ("FREE".equals(activation.policyScope())) reset(activation, at);
        for (String type : activation.changedQuotaTypes()) {
            if (activation.policySnapshot().rules().stream().noneMatch(rule -> rule.quotaType().equals(type))) throw new QuotaUnavailableException();
            store.insert(RECEIPTS, new Document("kind", "RECEIPT").append("activationId", activation.activationId())
                    .append("quotaType", type).append("policyScope", activation.policyScope()).append("policyVersion", activation.policyVersion())
                    .append("activationSequence", activation.activationSequence()).append("effectiveAt", Date.from(activation.effectiveAt()))
                    .append("completedAt", Date.from(at)).append("state", "COMPLETED"));
        }
    }

    private void reset(QuotaPolicyActivated activation, Instant at) {
        Map<String, EffectiveEntitlement> users = new HashMap<>();
        for (Document counter : store.find(COUNTERS, Criteria.where("quotaType").in(activation.changedQuotaTypes()))) {
            String userId = counter.getString("userId");
            EffectiveEntitlement entitlement = users.computeIfAbsent(userId, key -> entitlements.authoritative(key, at));
            if (!"FREE".equals(entitlement.policyScope()) || entitlement.policyVersion() != activation.policyVersion()
                    || !entitlement.policySnapshotId().equals(activation.policySnapshot().policySnapshotId())) continue;
            QuotaRule rule = QuotaService.rule(entitlement, counter.getString("quotaType"));
            QuotaService.CounterView view = QuotaService.view(counter, rule, at);
            if (!view.start().equals(instant(counter, "calendarWindowStartAt")) || view.start().isAfter(activation.effectiveAt())) continue;
            if (counter.get("lastAppliedActivationSequence") != null
                    && number(counter, "lastAppliedActivationSequence") >= activation.activationSequence()) continue;
            long retained = 0;
            for (Document ledger : store.find(LEDGER, QuotaService.key(userId, rule.quotaType())
                    .and("recordedAt").gte(Date.from(activation.effectiveAt())).lt(Date.from(view.end())))) {
                retained = Math.addExact(retained, number(ledger, "amount"));
            }
            String allowance = view.allowanceStart().equals(activation.effectiveAt()) ? view.allowanceId() : UUID.randomUUID().toString();
            boolean updated = store.update(COUNTERS, QuotaService.key(userId, rule.quotaType())
                    .and("revision").is(number(counter, "revision")).and("allowanceId").is(view.allowanceId())
                    .and("calendarWindowStartAt").is(Date.from(view.start())), new Update().set("usedAmount", retained)
                    .set("allowanceId", allowance).set("allowanceStartAt", Date.from(activation.effectiveAt()))
                    .set("lastAppliedActivationSequence", activation.activationSequence()).set("lastAppliedPolicyActivationId", activation.activationId())
                    .set("revision", number(counter, "revision") + 1).set("updatedAt", Date.from(at)));
            if (!updated) throw new OptimisticLockingFailureException("Counter changed during activation");
        }
    }
}
