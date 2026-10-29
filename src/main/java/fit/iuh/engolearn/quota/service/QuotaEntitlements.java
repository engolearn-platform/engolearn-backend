package fit.iuh.engolearn.quota.service;

import fit.iuh.engolearn.quota.contract.QuotaUnavailableException;
import fit.iuh.engolearn.quota.repository.QuotaStore;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import fit.iuh.engolearn.subscription.contract.EntitlementChanged;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlement;
import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshot;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRule;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static fit.iuh.engolearn.quota.repository.QuotaStore.ENTITLEMENTS;
import static fit.iuh.engolearn.quota.repository.QuotaStore.POLICIES;

@Service
public class QuotaEntitlements {
    private final QuotaStore store;
    private final EffectiveEntitlementProvider provider;
    private final QuotaTransactions transactions;
    private final Clock clock;

    public QuotaEntitlements(QuotaStore store, EffectiveEntitlementProvider provider, QuotaTransactions transactions, Clock clock) {
        this.store = store;
        this.provider = provider;
        this.transactions = transactions;
        this.clock = clock;
    }

    @PostConstruct
    void indexes() {
        if (transactions.available()) store.ensureIndexes();
    }

    public EffectiveEntitlement resolve(String userId, Instant at) {
        Document current = store.one(ENTITLEMENTS, Criteria.where("userId").is(userId));
        if (current != null && valid(current, at)) return decode(current);
        EffectiveEntitlement resolved = provider.resolveEffectiveEntitlement(userId, at);
        if (!Objects.equals(userId, resolved.userId())) throw new QuotaUnavailableException();
        EffectiveEntitlement selected = project(resolved);
        if (selected.effectiveAt().isAfter(at) || selected.validUntil() != null && !at.isBefore(selected.validUntil())) {
            throw new QuotaUnavailableException();
        }
        return selected;
    }

    public EffectiveEntitlement authoritative(String userId, Instant at) {
        EffectiveEntitlement next = provider.resolveEffectiveEntitlement(userId, at);
        if (!Objects.equals(userId, next.userId())) throw new QuotaUnavailableException();
        return project(next);
    }

    public void guardAcceptedUsage(String userId) {
        Document observed = store.one(ENTITLEMENTS, Criteria.where("userId").is(userId));
        if (observed == null || !store.update(ENTITLEMENTS, Criteria.where("userId").is(userId)
                .and("revision").is(number(observed, "revision")), new Update().inc("revision", 1L))) {
            throw new OptimisticLockingFailureException("Entitlement changed during usage");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEntitlementChanged(EntitlementChanged event) {
        transactions.event(() -> { project(event.entitlement()); return true; });
    }

    public EffectiveEntitlement project(EffectiveEntitlement next) {
        validate(next);
        saveSnapshot(next.policySnapshot());
        Document current = store.one(ENTITLEMENTS, Criteria.where("userId").is(next.userId()));
        if (current == null) {
            store.insert(ENTITLEMENTS, encode(next).append("revision", 0L));
            return next;
        }
        EffectiveEntitlement previous = decode(current);
        if (next.entitlementVersion() < previous.entitlementVersion()) return previous;
        if (next.entitlementVersion() == previous.entitlementVersion()) {
            if (!next.policyScope().equals(previous.policyScope())) throw new QuotaUnavailableException();
            if ("FREE".equals(next.policyScope())) {
                if (next.policyVersion() < previous.policyVersion()
                        || next.policyActivationSequence() < previous.policyActivationSequence()) return previous;
                if (next.policyVersion() == previous.policyVersion()
                        && !next.policySnapshotId().equals(previous.policySnapshotId())) throw new QuotaUnavailableException();
            } else if (!next.policySnapshotId().equals(previous.policySnapshotId())
                    || next.policyVersion() != previous.policyVersion()) throw new QuotaUnavailableException();
        }
        Document values = encode(next);
        long revision = number(current, "revision");
        Update update = new Update();
        values.forEach(update::set);
        update.set("revision", revision + 1).set("updatedAt", Date.from(clock.instant()));
        if (!store.update(ENTITLEMENTS, Criteria.where("userId").is(next.userId()).and("revision").is(revision), update)) {
            throw new OptimisticLockingFailureException("Entitlement changed");
        }
        return next;
    }

    public void saveSnapshot(QuotaPolicySnapshot snapshot) {
        validateSnapshot(snapshot);
        Document encoded = snapshot(snapshot);
        Document existing = store.one(POLICIES, Criteria.where("policySnapshotId").is(snapshot.policySnapshotId()));
        if (existing != null) {
            existing.remove("_id");
            if (!existing.equals(encoded)) throw new QuotaUnavailableException();
            return;
        }
        for (Document policy : store.all(POLICIES)) {
            for (Document old : policy.getList("rules", Document.class)) {
                for (QuotaRule rule : snapshot.rules()) {
                    if (rule.quotaType().equals(old.getString("quotaType"))
                            && (!rule.unit().equals(old.getString("unit")) || !rule.period().equals(old.getString("period")))) {
                        throw new QuotaUnavailableException();
                    }
                }
            }
        }
        store.insert(POLICIES, encoded);
    }

    private void validate(EffectiveEntitlement value) {
        if (value == null || blank(value.userId()) || value.entitlementVersion() < 0 || value.effectiveAt() == null
                || value.policyActivationSequence() < 0 || value.policyVersion() < 0 || blank(value.policyScope())
                || blank(value.policySnapshotId()) || value.policySnapshot() == null
                || !value.policySnapshotId().equals(value.policySnapshot().policySnapshotId())
                || !value.policyScope().equals(value.policySnapshot().policyScope())
                || value.policyVersion() != value.policySnapshot().policyVersion()
                || !"FREE".equals(value.policyScope()) && value.validUntil() == null) throw new QuotaUnavailableException();
    }

    private void validateSnapshot(QuotaPolicySnapshot snapshot) {
        if (snapshot == null || blank(snapshot.policySnapshotId()) || blank(snapshot.policyScope()) || snapshot.policyVersion() < 0) {
            throw new QuotaUnavailableException();
        }
        Set<String> types = new HashSet<>();
        for (QuotaRule rule : snapshot.rules()) {
            if (blank(rule.quotaType()) || blank(rule.displayName()) || blank(rule.unit()) || rule.limit() < 0
                    || rule.limit() > 9_007_199_254_740_991L || !Set.of("DAILY", "WEEKLY", "MONTHLY").contains(rule.period())
                    || !types.add(rule.quotaType())) throw new QuotaUnavailableException();
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean valid(Document document, Instant at) {
        Instant end = instant(document, "validUntil");
        return !instant(document, "effectiveAt").isAfter(at) && (end == null || at.isBefore(end));
    }

    private Document encode(EffectiveEntitlement value) {
        return new Document("userId", value.userId()).append("entitlementVersion", value.entitlementVersion())
                .append("policyScope", value.policyScope()).append("policySnapshotId", value.policySnapshotId())
                .append("policyVersion", value.policyVersion()).append("policyActivationSequence", value.policyActivationSequence())
                .append("effectiveAt", Date.from(value.effectiveAt()))
                .append("validUntil", value.validUntil() == null ? null : Date.from(value.validUntil()));
    }

    private EffectiveEntitlement decode(Document document) {
        Document policy = store.one(POLICIES, Criteria.where("policySnapshotId").is(document.getString("policySnapshotId")));
        if (policy == null) throw new QuotaUnavailableException();
        List<QuotaRule> rules = policy.getList("rules", Document.class).stream().map(rule -> new QuotaRule(
                rule.getString("quotaType"), rule.getString("displayName"), number(rule, "limit"), rule.getString("unit"),
                rule.getString("period"), rule.getInteger("displayOrder"))).toList();
        QuotaPolicySnapshot snapshot = new QuotaPolicySnapshot(policy.getString("policySnapshotId"), policy.getString("policyScope"),
                number(policy, "policyVersion"), rules);
        EffectiveEntitlement value = new EffectiveEntitlement(document.getString("userId"), number(document, "entitlementVersion"),
                document.getString("policyScope"), document.getString("policySnapshotId"), number(document, "policyVersion"),
                number(document, "policyActivationSequence"), instant(document, "effectiveAt"), instant(document, "validUntil"), snapshot);
        validate(value);
        validateSnapshot(snapshot);
        return value;
    }

    private Document snapshot(QuotaPolicySnapshot snapshot) {
        return new Document("policySnapshotId", snapshot.policySnapshotId()).append("policyScope", snapshot.policyScope())
                .append("policyVersion", snapshot.policyVersion()).append("timezone", "Asia/Ho_Chi_Minh")
                .append("rules", snapshot.rules().stream().map(rule -> new Document("quotaType", rule.quotaType())
                        .append("displayName", rule.displayName()).append("limit", rule.limit()).append("unit", rule.unit())
                        .append("period", rule.period()).append("displayOrder", rule.displayOrder())).toList());
    }

    static long number(Document document, String key) { return ((Number) document.get(key)).longValue(); }
    static Instant instant(Document document, String key) { Date value = document.getDate(key); return value == null ? null : value.toInstant(); }
}
