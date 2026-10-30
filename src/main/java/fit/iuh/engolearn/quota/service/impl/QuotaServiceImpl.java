package fit.iuh.engolearn.quota.service.impl;
import fit.iuh.engolearn.quota.service.*;
import fit.iuh.engolearn.quota.model.*;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.mapper.QuotaMapper;
import fit.iuh.engolearn.quota.repository.QuotaRepository;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.exception.*;
import fit.iuh.engolearn.quota.contract.dto.*;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageItemResponse;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageSummaryResponse;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlementDto;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRuleDto;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService, QuotaConsumption, QuotaUsageRecording, QuotaAvailability {
    private static final long MAX = 9_007_199_254_740_991L;
    private final QuotaRepository store;
    private final QuotaEntitlements entitlements;
    private final QuotaTransactions transactions;
    private final Clock clock;


    @Override
    public UsageResultDto consume(UsageCommandDto command) { return write(command, UsageOperation.CONSUME); }

    @Override
    public UsageResultDto recordUsage(UsageCommandDto command) { return write(command, UsageOperation.RECORD_USAGE); }

    private UsageResultDto write(UsageCommandDto command, UsageOperation operation) {
        Objects.requireNonNull(command, "command");
        try {
            return transactions.execute(() -> accept(command, operation), () -> accepted(command, operation));
        } catch (QuotaIdempotencyConflictException | QuotaUnavailableException | IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    private UsageResultDto accept(UsageCommandDto command, UsageOperation operation) {
        UsageResultDto prior = accepted(command, operation);
        if (prior != null) return prior;
        Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        EffectiveEntitlementDto entitlement = entitlements.resolve(command.userId(), at);
        QuotaRuleDto rule = entitlements.rule(entitlement, command.quotaType());
        QuotaUsageCounter previous = store.findCounter(command.userId(), command.quotaType());
        CounterView view = CounterView.from(previous, rule.unit(), rule.period(), at);
        if (operation == UsageOperation.CONSUME && command.amount() > rule.limit() - Math.min(rule.limit(), view.used())) {
            return new UsageResultDto(false, null, command.amount(), null, view.allowanceId(), QuotaMapper.availability(rule, view));
        }
        if (command.amount() > MAX - view.used()) throw new QuotaUnavailableException();
        entitlements.guardAcceptedUsage(command.userId());
        long usedAfter = view.used() + command.amount();
        String allowance = view.allowanceId() == null ? UUID.randomUUID().toString() : view.allowanceId();
        QuotaUsageCounter counter = QuotaMapper.counter(command.userId(), rule, view, allowance, usedAfter,
                previous == null ? 0L : previous.getRevision() + 1, at);
        if (previous == null) store.insertCounter(counter);
        else {
            Long maximumPriorUsed = operation == UsageOperation.CONSUME && view.start().equals(previous.getCalendarWindowStartAt())
                    ? rule.limit() - command.amount() : null;
            if (!store.replaceCounter(previous, counter, maximumPriorUsed)) throw new OptimisticLockingFailureException("Counter changed");
        }
        String ledgerId = UUID.randomUUID().toString();
        CounterView acceptedView = new CounterView(usedAfter, view.start(), view.end(), view.allowanceStart(), allowance);
        AvailabilityResultDto availability = QuotaMapper.availability(rule, acceptedView);
        store.insertUsage(QuotaMapper.ledger(ledgerId, command, operation, at, entitlement, rule, view, allowance, usedAfter));
        return new UsageResultDto(true, ledgerId, command.amount(), at, allowance, availability);
    }

    private UsageResultDto accepted(UsageCommandDto command, UsageOperation operation) {
        QuotaUsageLedger ledger = store.findAcceptedUsage(command.userId(), command.sourceFeature(), command.idempotencyKey());
        if (ledger == null) return null;
        if (operation != ledger.getOperation() || !command.quotaType().equals(ledger.getQuotaType())
                || command.amount() != ledger.getAmount() || !Objects.equals(command.sourceReference(), ledger.getSourceReference())
                || !Objects.equals(command.occurredAt() == null ? null : command.occurredAt().toString(), ledger.getOccurredAt()))
            throw new QuotaIdempotencyConflictException();
        return new UsageResultDto(true, ledger.getLedgerId(), command.amount(), ledger.getRecordedAt(), ledger.getAllowanceId(), QuotaMapper.availability(ledger));
    }

    @Override
    public AvailabilityResultDto checkAvailability(String userId, String quotaType) {
        requireIdentifier(userId);
        requireIdentifier(quotaType);
        try {
            return transactions.execute(() -> {
                Instant at = clock.instant();
                QuotaRuleDto rule = entitlements.rule(entitlements.resolve(userId, at), quotaType);
                return QuotaMapper.availability(rule, CounterView.from(store.findCounter(userId, quotaType), rule.unit(), rule.period(), at));
            });
        } catch (IllegalArgumentException | QuotaUnavailableException exception) { throw exception; }
        catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    public QuotaUsageSummaryResponse usage(String userId) {
        requireIdentifier(userId);
        try {
            return transactions.execute(() -> {
                Instant at = clock.instant();
                EffectiveEntitlementDto entitlement = entitlements.resolve(userId, at);
                List<QuotaUsageItemResponse> items = entitlement.policySnapshot().rules().stream()
                        .sorted(Comparator.comparingInt(QuotaRuleDto::displayOrder).thenComparing(QuotaRuleDto::quotaType))
                        .map(rule -> {
                            AvailabilityResultDto result = QuotaMapper.availability(rule,
                                    CounterView.from(store.findCounter(userId, rule.quotaType()), rule.unit(), rule.period(), at));
                            return QuotaMapper.usageItem(rule, result);
                        }).toList();
                return new QuotaUsageSummaryResponse(items);
            });
        } catch (QuotaUnavailableException exception) { throw exception; }
        catch (RuntimeException exception) { throw new QuotaUnavailableException(exception); }
    }

    private void requireIdentifier(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier is required");
    }

}
