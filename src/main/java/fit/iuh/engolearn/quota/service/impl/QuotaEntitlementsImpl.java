package fit.iuh.engolearn.quota.service.impl;
import fit.iuh.engolearn.quota.service.*;
import fit.iuh.engolearn.quota.model.*;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.mapper.QuotaMapper;
import fit.iuh.engolearn.quota.repository.QuotaRepository;
import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import fit.iuh.engolearn.subscription.contract.event.EntitlementChangedEvent;
import fit.iuh.engolearn.subscription.contract.dto.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;

@Service
@RequiredArgsConstructor
@DependsOn("quotaIndexes")
public class QuotaEntitlementsImpl implements QuotaEntitlements {
    private final QuotaRepository store;
    private final EffectiveEntitlementProvider provider;
    private final QuotaTransactions transactions;
    private final Clock clock;

    public EffectiveEntitlementDto resolve(String userId, Instant at) {
        QuotaEntitlementProjection current = store.findEntitlement(userId);
        QuotaActivationReceipt freeHead = current != null && "FREE".equals(String.valueOf(current.getPolicyScope()))
                ? store.findActivationHead("FREE") : null;
        boolean staleFree = freeHead != null && freeHead.getActivationSequence() > current.getPolicyActivationSequence();
        if (current != null && !staleFree && !current.getEffectiveAt().isAfter(at) &&
                (current.getValidUntil() == null || at.isBefore(current.getValidUntil()))) return decode(current);
        EffectiveEntitlementDto resolved = provider.resolveEffectiveEntitlement(userId, at);
        if (!Objects.equals(userId, resolved.userId())) throw new QuotaUnavailableException();
        EffectiveEntitlementDto selected = project(resolved);
        if (selected.effectiveAt().isAfter(at) || selected.validUntil() != null && !at.isBefore(selected.validUntil()))
            throw new QuotaUnavailableException();
        return selected;
    }
    public EffectiveEntitlementDto authoritative(String userId, Instant at) {
        EffectiveEntitlementDto next = provider.resolveEffectiveEntitlement(userId, at);
        if (!Objects.equals(userId, next.userId())) throw new QuotaUnavailableException();
        return project(next);
    }
    public void guardAcceptedUsage(String userId) {
        QuotaEntitlementProjection observed = store.findEntitlement(userId);
        if (observed == null || !store.guardEntitlement(userId, observed.getRevision()))
            throw new OptimisticLockingFailureException("Entitlement changed during usage");
    }
    public void onEntitlementChanged(EntitlementChangedEvent event) {
        transactions.event(() -> { project(event.entitlement()); return true; });
    }
    public EffectiveEntitlementDto project(EffectiveEntitlementDto next) {
        validate(next);
        saveSnapshot(next.policySnapshot());
        QuotaEntitlementProjection current = store.findEntitlement(next.userId());
        if (current == null) {
            store.insertEntitlement(QuotaMapper.projection(next));
            return next;
        }
        EffectiveEntitlementDto previous = decode(current);
        if (next.entitlementVersion() < previous.entitlementVersion()) return previous;
        if (next.entitlementVersion() == previous.entitlementVersion()) {
            if (!next.policyScope().equals(previous.policyScope())) throw new QuotaUnavailableException();
            if ("FREE".equals(next.policyScope())) {
                if (next.policyVersion() < previous.policyVersion() ||
                        next.policyActivationSequence() < previous.policyActivationSequence()) return previous;
                if (next.policyVersion() == previous.policyVersion() &&
                        !next.policySnapshotId().equals(previous.policySnapshotId())) throw new QuotaUnavailableException();
            } else if (!next.policySnapshotId().equals(previous.policySnapshotId()) ||
                    next.policyVersion() != previous.policyVersion()) throw new QuotaUnavailableException();
        }
        QuotaEntitlementProjection values = QuotaMapper.projection(next);
        values.setUpdatedAt(clock.instant());
        if (!store.replaceEntitlement(current.getRevision(), values)) throw new OptimisticLockingFailureException("Entitlement changed");
        return next;
    }
    public void saveSnapshot(QuotaPolicySnapshotDto snapshot) {
        validateSnapshot(snapshot);
        QuotaPolicySnapshot existing = store.findSnapshot(snapshot.policySnapshotId());
        if (existing != null) {
            if (!"Asia/Ho_Chi_Minh".equals(existing.getTimezone()) || !QuotaMapper.snapshot(existing).equals(snapshot))
                throw new QuotaUnavailableException();
            return;
        }
        for (QuotaPolicySnapshot policy : store.findSnapshots())
            for (var old : policy.getRules())
                for (QuotaRuleDto rule : snapshot.rules())
                    if (rule.quotaType().equals(old.getQuotaType()) &&
                            (!rule.unit().equals(old.getUnit()) || !rule.period().equals(old.getPeriod().name())))
                        throw new QuotaUnavailableException();
        store.insertSnapshot(QuotaMapper.snapshot(snapshot));
    }
    public QuotaRuleDto rule(EffectiveEntitlementDto entitlement, String type) {
        return entitlement.policySnapshot().rules().stream().filter(rule -> rule.quotaType().equals(type)).findFirst()
                .orElseThrow(QuotaUnavailableException::new);
    }
    private EffectiveEntitlementDto decode(QuotaEntitlementProjection projection) {
        QuotaPolicySnapshot policy = store.findSnapshot(projection.getPolicySnapshotId());
        if (policy == null) throw new QuotaUnavailableException();
        EffectiveEntitlementDto value = QuotaMapper.entitlement(projection, policy);
        validate(value);
        validateSnapshot(value.policySnapshot());
        return value;
    }
    private void validate(EffectiveEntitlementDto value) {
        if (value == null || blank(value.userId()) || value.entitlementVersion() < 0 || value.effectiveAt() == null
                || value.policyActivationSequence() < 0 || value.policyVersion() < 0 || blank(value.policyScope())
                || blank(value.policySnapshotId()) || value.policySnapshot() == null
                || !value.policySnapshotId().equals(value.policySnapshot().policySnapshotId())
                || !value.policyScope().equals(value.policySnapshot().policyScope())
                || value.policyVersion() != value.policySnapshot().policyVersion()
                || !"FREE".equals(value.policyScope()) && value.validUntil() == null) throw new QuotaUnavailableException();
    }

    private void validateSnapshot(QuotaPolicySnapshotDto snapshot) {
        if (snapshot == null || blank(snapshot.policySnapshotId()) || blank(snapshot.policyScope()) || snapshot.policyVersion() < 0) {
            throw new QuotaUnavailableException();
        }
        Set<String> types = new HashSet<>();
        for (QuotaRuleDto rule : snapshot.rules()) {
            if (blank(rule.quotaType()) || blank(rule.displayName()) || blank(rule.unit()) || rule.limit() < 0
                    || rule.limit() > 9_007_199_254_740_991L || !Set.of("DAILY", "WEEKLY", "MONTHLY").contains(rule.period())
                    || !types.add(rule.quotaType())) throw new QuotaUnavailableException();
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
