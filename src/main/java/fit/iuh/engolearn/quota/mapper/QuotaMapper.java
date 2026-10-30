package fit.iuh.engolearn.quota.mapper;

import fit.iuh.engolearn.quota.model.*;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.model.vo.QuotaRuleVo;
import fit.iuh.engolearn.quota.contract.dto.*;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageItemResponse;
import fit.iuh.engolearn.subscription.contract.dto.*;
import java.time.Instant;

public final class QuotaMapper {
    private QuotaMapper() {}

    public static QuotaPolicySnapshot snapshot(QuotaPolicySnapshotDto value) {
        return QuotaPolicySnapshot.builder().policySnapshotId(value.policySnapshotId())
                .policyScope(PolicyScope.valueOf(value.policyScope())).policyVersion(value.policyVersion())
                .timezone("Asia/Ho_Chi_Minh").rules(value.rules().stream().map(r -> QuotaRuleVo.builder()
                        .quotaType(r.quotaType()).displayName(r.displayName()).limit(r.limit()).unit(r.unit())
                        .period(QuotaPeriod.valueOf(r.period())).displayOrder(r.displayOrder()).build()).toList()).build();
    }
    public static QuotaPolicySnapshotDto snapshot(QuotaPolicySnapshot value) {
        return new QuotaPolicySnapshotDto(value.getPolicySnapshotId(), value.getPolicyScope().name(), value.getPolicyVersion(),
                value.getRules().stream().map(r -> new QuotaRuleDto(r.getQuotaType(), r.getDisplayName(), r.getLimit(),
                        r.getUnit(), r.getPeriod().name(), r.getDisplayOrder())).toList());
    }
    public static QuotaEntitlementProjection projection(EffectiveEntitlementDto value) {
        return QuotaEntitlementProjection.builder().userId(value.userId()).entitlementVersion(value.entitlementVersion())
                .policyScope(PolicyScope.valueOf(value.policyScope())).policySnapshotId(value.policySnapshotId())
                .policyVersion(value.policyVersion()).policyActivationSequence(value.policyActivationSequence())
                .effectiveAt(value.effectiveAt()).validUntil(value.validUntil()).revision(0L).build();
    }
    public static EffectiveEntitlementDto entitlement(QuotaEntitlementProjection value, QuotaPolicySnapshot snapshot) {
        return new EffectiveEntitlementDto(value.getUserId(), value.getEntitlementVersion(), value.getPolicyScope().name(),
                value.getPolicySnapshotId(), value.getPolicyVersion(), value.getPolicyActivationSequence(),
                value.getEffectiveAt(), value.getValidUntil(), snapshot(snapshot));
    }
    public static QuotaUsageCounter counter(String userId, QuotaRuleDto rule, CounterView view, String allowance,
            long usedAfter, long revision, Instant at) {
        return QuotaUsageCounter.builder().userId(userId).quotaType(rule.quotaType()).usedAmount(usedAfter)
                .unit(rule.unit()).period(QuotaPeriod.valueOf(rule.period())).calendarWindowStartAt(view.start())
                .calendarWindowEndAt(view.end()).allowanceStartAt(view.allowanceStart()).allowanceId(allowance)
                .updatedAt(at).revision(revision).build();
    }
    public static QuotaUsageLedger ledger(String ledgerId, UsageCommandDto command, UsageOperation operation,
            Instant at, EffectiveEntitlementDto entitlement, QuotaRuleDto rule, CounterView view, String allowance, long usedAfter) {
        return QuotaUsageLedger.builder().ledgerId(ledgerId).userId(command.userId()).quotaType(command.quotaType())
                .amount(command.amount()).operation(operation).sourceFeature(command.sourceFeature())
                .idempotencyKey(command.idempotencyKey()).sourceReference(command.sourceReference())
                .occurredAt(command.occurredAt() == null ? null : command.occurredAt().toString()).recordedAt(at)
                .policyScope(PolicyScope.valueOf(entitlement.policyScope())).policySnapshotId(entitlement.policySnapshotId())
                .policyVersion(entitlement.policyVersion()).allowanceId(allowance).allowanceStartAt(view.allowanceStart())
                .calendarWindowStartAt(view.start()).calendarWindowEndAt(view.end()).usedAfter(usedAfter)
                .limitAtRecord(rule.limit()).unit(rule.unit()).period(QuotaPeriod.valueOf(rule.period())).build();
    }
    public static AvailabilityResultDto availability(QuotaRuleDto rule, CounterView view) {
        return new AvailabilityResultDto(rule.quotaType(), view.used(), rule.limit(), Math.max(0, rule.limit() - view.used()),
                rule.unit(), rule.period(), view.start(), view.end(), view.allowanceStart(), view.status(rule.limit()));
    }
    public static AvailabilityResultDto availability(QuotaUsageLedger ledger) {
        long used = ledger.getUsedAfter(), limit = ledger.getLimitAtRecord();
        return new AvailabilityResultDto(ledger.getQuotaType(), used, limit, Math.max(0, limit - used), ledger.getUnit(),
                ledger.getPeriod().name(), ledger.getCalendarWindowStartAt(), ledger.getCalendarWindowEndAt(),
                ledger.getAllowanceStartAt(), CounterView.status(used, limit));
    }
    public static QuotaUsageItemResponse usageItem(QuotaRuleDto rule, AvailabilityResultDto result) {
        return new QuotaUsageItemResponse(result.quotaType(), rule.displayName(), result.used(), result.limit(), result.remaining(),
                result.unit(), result.period(), result.windowStartAt(), result.windowEndsAt(), result.allowanceStartAt(),
                result.status(), result.used() > result.limit() ? result.used() - result.limit() : null);
    }
}
