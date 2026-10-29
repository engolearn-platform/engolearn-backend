package fit.iuh.engolearn.quota.service;

import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.dto.*;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageItem;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageSummary;
import fit.iuh.engolearn.quota.repository.QuotaStore;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlement;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRule;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static fit.iuh.engolearn.quota.repository.QuotaStore.*;
import static fit.iuh.engolearn.quota.service.QuotaEntitlements.*;

@Service
public class QuotaService implements QuotaConsumption, QuotaUsageRecording, QuotaAvailability {
    private static final long MAX = 9_007_199_254_740_991L;
    private final QuotaStore store;
    private final QuotaEntitlements entitlements;
    private final QuotaTransactions transactions;
    private final Clock clock;

    public QuotaService(QuotaStore store, QuotaEntitlements entitlements, QuotaTransactions transactions, Clock clock) {
        this.store = store;
        this.entitlements = entitlements;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public UsageResult consume(UsageCommand command) { return write(command, "CONSUME"); }

    @Override
    public UsageResult recordUsage(UsageCommand command) { return write(command, "RECORD_USAGE"); }

    private UsageResult write(UsageCommand command, String operation) {
        Objects.requireNonNull(command, "command");
        try {
            return transactions.execute(() -> accept(command, operation), () -> accepted(command, operation));
        } catch (QuotaIdempotencyConflictException | QuotaUnavailableException | IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    private UsageResult accept(UsageCommand command, String operation) {
        UsageResult prior = accepted(command, operation);
        if (prior != null) return prior;
        Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        EffectiveEntitlement entitlement = entitlements.resolve(command.userId(), at);
        QuotaRule rule = rule(entitlement, command.quotaType());
        Document previous = store.one(COUNTERS, key(command.userId(), command.quotaType()));
        CounterView view = view(previous, rule, at);
        if (operation.equals("CONSUME") && command.amount() > rule.limit() - Math.min(rule.limit(), view.used())) {
            return new UsageResult(false, null, command.amount(), null, view.allowanceId(), availability(rule, view));
        }
        if (command.amount() > MAX - view.used()) throw new QuotaUnavailableException();
        entitlements.guardAcceptedUsage(command.userId());
        long usedAfter = view.used() + command.amount();
        String allowance = view.allowanceId() == null ? UUID.randomUUID().toString() : view.allowanceId();
        Document counter = new Document("userId", command.userId()).append("quotaType", command.quotaType())
                .append("usedAmount", usedAfter).append("unit", rule.unit()).append("period", rule.period())
                .append("calendarWindowStartAt", Date.from(view.start())).append("calendarWindowEndAt", Date.from(view.end()))
                .append("allowanceStartAt", Date.from(view.allowanceStart())).append("allowanceId", allowance)
                .append("updatedAt", Date.from(at)).append("revision", previous == null ? 0L : number(previous, "revision") + 1);
        if (previous == null) store.insert(COUNTERS, counter);
        else {
            Criteria predicate = key(command.userId(), command.quotaType()).and("revision").is(number(previous, "revision"))
                    .and("calendarWindowStartAt").is(previous.getDate("calendarWindowStartAt"))
                    .and("allowanceId").is(previous.getString("allowanceId"));
            if (operation.equals("CONSUME") && view.start().equals(instant(previous, "calendarWindowStartAt"))) {
                predicate.and("usedAmount").lte(rule.limit() - command.amount());
            }
            Update update = new Update();
            counter.forEach(update::set);
            if (!store.update(COUNTERS, predicate, update)) throw new OptimisticLockingFailureException("Counter changed");
        }
        String ledgerId = UUID.randomUUID().toString();
        CounterView acceptedView = new CounterView(usedAfter, view.start(), view.end(), view.allowanceStart(), allowance);
        AvailabilityResult availability = availability(rule, acceptedView);
        Document ledger = new Document("ledgerId", ledgerId).append("userId", command.userId())
                .append("quotaType", command.quotaType()).append("amount", command.amount()).append("operation", operation)
                .append("sourceFeature", command.sourceFeature()).append("idempotencyKey", command.idempotencyKey())
                .append("sourceReference", command.sourceReference()).append("occurredAt", command.occurredAt() == null ? null : command.occurredAt().toString())
                .append("recordedAt", Date.from(at)).append("policyScope", entitlement.policyScope())
                .append("policySnapshotId", entitlement.policySnapshotId()).append("policyVersion", entitlement.policyVersion())
                .append("allowanceId", allowance).append("allowanceStartAt", Date.from(view.allowanceStart()))
                .append("calendarWindowStartAt", Date.from(view.start())).append("calendarWindowEndAt", Date.from(view.end()))
                .append("usedAfter", usedAfter).append("limitAtRecord", rule.limit()).append("unit", rule.unit()).append("period", rule.period());
        store.insert(LEDGER, ledger);
        return new UsageResult(true, ledgerId, command.amount(), at, allowance, availability);
    }

    private UsageResult accepted(UsageCommand command, String operation) {
        Document ledger = store.one(LEDGER, Criteria.where("userId").is(command.userId()).and("sourceFeature").is(command.sourceFeature())
                .and("idempotencyKey").is(command.idempotencyKey()));
        if (ledger == null) return null;
        if (!operation.equals(ledger.getString("operation")) || !command.quotaType().equals(ledger.getString("quotaType"))
                || command.amount() != number(ledger, "amount") || !Objects.equals(command.sourceReference(), ledger.getString("sourceReference"))
                || !Objects.equals(command.occurredAt() == null ? null : command.occurredAt().toString(), ledger.getString("occurredAt"))) throw new QuotaIdempotencyConflictException();
        long used = number(ledger, "usedAfter");
        long limit = number(ledger, "limitAtRecord");
        AvailabilityResult availability = new AvailabilityResult(command.quotaType(), used, limit, Math.max(0, limit - used),
                ledger.getString("unit"), ledger.getString("period"), instant(ledger, "calendarWindowStartAt"),
                instant(ledger, "calendarWindowEndAt"), instant(ledger, "allowanceStartAt"), status(used, limit));
        return new UsageResult(true, ledger.getString("ledgerId"), command.amount(), instant(ledger, "recordedAt"),
                ledger.getString("allowanceId"), availability);
    }

    @Override
    public AvailabilityResult checkAvailability(String userId, String quotaType) {
        requireIdentifier(userId);
        requireIdentifier(quotaType);
        try {
            return transactions.execute(() -> {
                Instant at = clock.instant();
                QuotaRule rule = rule(entitlements.resolve(userId, at), quotaType);
                return availability(rule, view(store.one(COUNTERS, key(userId, quotaType)), rule, at));
            });
        } catch (IllegalArgumentException | QuotaUnavailableException exception) { throw exception; }
        catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    public QuotaUsageSummary usage(String userId) {
        requireIdentifier(userId);
        try {
            return transactions.execute(() -> {
                Instant at = clock.instant();
                EffectiveEntitlement entitlement = entitlements.resolve(userId, at);
                List<QuotaUsageItem> items = entitlement.policySnapshot().rules().stream()
                        .sorted(Comparator.comparingInt(QuotaRule::displayOrder).thenComparing(QuotaRule::quotaType))
                        .map(rule -> {
                            AvailabilityResult result = availability(rule, view(store.one(COUNTERS, key(userId, rule.quotaType())), rule, at));
                            return new QuotaUsageItem(result.quotaType(), rule.displayName(), result.used(), result.limit(), result.remaining(),
                                    result.unit(), result.period(), result.windowStartAt(), result.windowEndsAt(), result.allowanceStartAt(),
                                    result.status(), result.used() > result.limit() ? result.used() - result.limit() : null);
                        }).toList();
                return new QuotaUsageSummary(items);
            });
        } catch (QuotaUnavailableException exception) { throw exception; }
        catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    private void requireIdentifier(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier is required");
    }

    static Criteria key(String userId, String type) { return Criteria.where("userId").is(userId).and("quotaType").is(type); }
    static QuotaRule rule(EffectiveEntitlement entitlement, String type) {
        return entitlement.policySnapshot().rules().stream().filter(rule -> rule.quotaType().equals(type)).findFirst()
                .orElseThrow(QuotaUnavailableException::new);
    }

    static CounterView view(Document counter, QuotaRule rule, Instant at) {
        QuotaCalendar.Window window = QuotaCalendar.window(at, rule.period());
        if (counter == null) return new CounterView(0, window.start(), window.end(), window.start(), null);
        long used = number(counter, "usedAmount");
        Instant start = instant(counter, "calendarWindowStartAt");
        Instant end = instant(counter, "calendarWindowEndAt");
        Instant allowanceStart = instant(counter, "allowanceStartAt");
        if (used < 0 || used > MAX || !rule.unit().equals(counter.getString("unit")) || !rule.period().equals(counter.getString("period"))
                || start == null || end == null || allowanceStart == null || counter.getString("allowanceId") == null
                || number(counter, "revision") < 0 || !end.isAfter(start) || allowanceStart.isBefore(start) || !allowanceStart.isBefore(end)
                || !QuotaCalendar.window(start, rule.period()).start().equals(start)
                || !QuotaCalendar.window(start, rule.period()).end().equals(end) || start.isAfter(window.start())) throw new QuotaUnavailableException();
        if (!start.equals(window.start())) return new CounterView(0, window.start(), window.end(), window.start(), null);
        return new CounterView(used, start, end, allowanceStart, counter.getString("allowanceId"));
    }

    static AvailabilityResult availability(QuotaRule rule, CounterView view) {
        return new AvailabilityResult(rule.quotaType(), view.used(), rule.limit(), Math.max(0, rule.limit() - view.used()), rule.unit(),
                rule.period(), view.start(), view.end(), view.allowanceStart(), status(view.used(), rule.limit()));
    }

    private static String status(long used, long limit) { return used > limit ? "OVER_LIMIT" : used == limit ? "EXHAUSTED" : "AVAILABLE"; }
    record CounterView(long used, Instant start, Instant end, Instant allowanceStart, String allowanceId) {}
}
