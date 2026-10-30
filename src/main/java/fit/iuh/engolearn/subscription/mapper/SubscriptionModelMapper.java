package fit.iuh.engolearn.subscription.mapper;

import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse.PublicationResultResponse;
import fit.iuh.engolearn.subscription.contract.dto.*;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.*;

public final class SubscriptionModelMapper {
    private SubscriptionModelMapper() {}

    public static PlanCardResponse sortedPlanCard(PlanCardVo plan) {
        return new PlanCardResponse(plan.getPlanCode().name(), plan.getDisplayName(), plan.getDescription(),
                plan.getBenefits().stream().sorted(Comparator.comparingInt(PlanBenefitVo::getSortOrder)
                        .thenComparing(PlanBenefitVo::getCode)).map(SubscriptionModelMapper::toDto).toList(),
                plan.getBillingOptions().stream().map(SubscriptionModelMapper::toDto).toList());
    }

    public static EffectiveEntitlementDto entitlement(String userId, long entitlementVersion,
            QuotaPolicySnapshotVo snapshot, long activationSequence, Instant effectiveAt, Instant validUntil) {
        return entitlement(userId, entitlementVersion, snapshot.getPolicyScope().name(),
                snapshot.getPolicySnapshotId(), snapshot.getPolicyVersion(), snapshot, activationSequence,
                effectiveAt, validUntil);
    }

    public static EffectiveEntitlementDto entitlement(String userId, long entitlementVersion, String scope,
            String snapshotId, long policyVersion, QuotaPolicySnapshotVo snapshot, long activationSequence,
            Instant effectiveAt, Instant validUntil) {
        return new EffectiveEntitlementDto(userId, entitlementVersion, scope, snapshotId, policyVersion,
                activationSequence, effectiveAt, validUntil, toDto(snapshot));
    }

    public static ConfigurationContentRequest adminContent(PolicyVersion policy, PlanVersion offer) {
        CurrentBenefitsVo benefits = policy.getBenefits();
        return new ConfigurationContentRequest(benefits.getDisplayName(), offer == null ? null : offer.getPlan().getDescription(),
                offer == null ? 0 : offer.getDisplayOrder(),
                benefits.getBenefits().stream().map(b -> new PlanBenefitRequest(b.getCode(), b.getTitle(), b.getDescription(),
                        b.getDisplayValue(), b.getSortOrder())).toList(),
                offer == null ? List.of() : offer.getPlan().getBillingOptions().stream()
                        .map(b -> new PlanBillingOptionRequest(b.getBillingPeriod().name(), b.getAmount(), b.getCurrency())).toList(),
                policy.getSnapshot().getRules().stream().map(r -> new QuotaRuleRequest(r.getQuotaType(), r.getDisplayName(),
                        r.getUnit(), r.getPeriod().name(), r.getLimit(), r.getDisplayOrder())).toList());
    }

    public static PolicyVersion policyVersion(String id, String scope, long version, Instant effectiveAt,
            String planCode, ConfigurationContentRequest content) {
        return policyVersion(id, scope, version, effectiveAt,
                new QuotaPolicySnapshotDto(id, scope, version, content.quotaRules().stream()
                        .map(r -> new QuotaRuleDto(r.quotaType(), r.displayName(), r.limit(), r.unit(), r.period(),
                                r.displayOrder())).toList()),
                new CurrentBenefitsResponse(planCode, content.displayName(), id, planBenefits(content)));
    }

    public static PlanVersion planVersion(String id, String scope, long version, Instant effectiveAt,
            String planCode, ConfigurationContentRequest content) {
        return planVersion(id, scope, version, effectiveAt, true, content.displayOrder(),
                new PlanCardResponse(planCode, content.displayName(), content.description(), planBenefits(content),
                        content.billingOptions().stream()
                                .map(b -> new PlanBillingOptionResponse(b.billingPeriod(), b.amount(), b.currency())).toList()));
    }

    private static List<PlanBenefitResponse> planBenefits(ConfigurationContentRequest content) {
        return content.benefits().stream().map(b -> new PlanBenefitResponse(b.code(), b.title(), b.description(),
                b.displayValue(), b.sortOrder())).toList();
    }

    public static SubscriptionPeriod subscriptionPeriod(String id, String userId, String kind, String state,
            Instant startAt, Instant endAt, PolicyVersion policy) {
        QuotaPolicySnapshotVo snapshot = policy.getSnapshot();
        CurrentBenefitsVo benefits = policy.getBenefits();
        return SubscriptionPeriod.builder().id(id).userId(userId).kind(PeriodKind.valueOf(kind))
                .state(PeriodState.valueOf(state)).periodStartAt(startAt).periodEndAt(endAt)
                .snapshot(QuotaPolicySnapshotVo.builder().policySnapshotId(snapshot.getPolicySnapshotId())
                        .policyScope(snapshot.getPolicyScope()).policyVersion(snapshot.getPolicyVersion())
                        .rules(snapshot.getRules().stream().map(r -> QuotaRuleVo.builder()
                                .quotaType(r.getQuotaType()).displayName(r.getDisplayName()).limit(r.getLimit())
                                .unit(r.getUnit()).period(r.getPeriod()).displayOrder(r.getDisplayOrder()).build()).toList())
                        .build())
                .benefits(CurrentBenefitsVo.builder().planCode(benefits.getPlanCode())
                        .displayName(benefits.getDisplayName()).policySnapshotId(benefits.getPolicySnapshotId())
                        .benefits(benefits.getBenefits().stream().map(b -> PlanBenefitVo.builder().code(b.getCode())
                                .title(b.getTitle()).description(b.getDescription()).displayValue(b.getDisplayValue())
                                .sortOrder(b.getSortOrder()).build()).toList()).build())
                .prices(List.of()).build();
    }

    public static PlanBenefitVo toVo(PlanBenefitResponse value) {
        if (value == null) return null;
        return PlanBenefitVo.builder().code(value.code()).title(value.title()).description(value.description()).displayValue(value.displayValue()).sortOrder(value.sortOrder()).build();
    }

    public static PlanBenefitResponse toDto(PlanBenefitVo value) {
        if (value == null) return null;
        return new PlanBenefitResponse(value.getCode(), value.getTitle(), value.getDescription(), value.getDisplayValue(), value.getSortOrder());
    }

    public static PlanBillingOptionVo toVo(PlanBillingOptionResponse value) {
        if (value == null) return null;
        return PlanBillingOptionVo.builder().billingPeriod(BillingPeriod.valueOf(value.billingPeriod())).amount(value.amount()).currency(value.currency()).build();
    }

    public static PlanBillingOptionResponse toDto(PlanBillingOptionVo value) {
        if (value == null) return null;
        return new PlanBillingOptionResponse(value.getBillingPeriod().name(), value.getAmount(), value.getCurrency());
    }

    public static PlanCardVo toVo(PlanCardResponse value) {
        if (value == null) return null;
        return PlanCardVo.builder().planCode(PlanCode.valueOf(value.planCode())).displayName(value.displayName()).description(value.description()).benefits(value.benefits().stream().map(SubscriptionModelMapper::toVo).toList()).billingOptions(value.billingOptions().stream().map(SubscriptionModelMapper::toVo).toList()).build();
    }

    public static PlanCardResponse toDto(PlanCardVo value) {
        if (value == null) return null;
        return new PlanCardResponse(value.getPlanCode().name(), value.getDisplayName(), value.getDescription(), value.getBenefits().stream().map(SubscriptionModelMapper::toDto).toList(), value.getBillingOptions().stream().map(SubscriptionModelMapper::toDto).toList());
    }

    public static CurrentBenefitsVo toVo(CurrentBenefitsResponse value) {
        if (value == null) return null;
        return CurrentBenefitsVo.builder().planCode(PlanCode.valueOf(value.planCode())).displayName(value.displayName()).policySnapshotId(value.policySnapshotId()).benefits(value.benefits().stream().map(SubscriptionModelMapper::toVo).toList()).build();
    }

    public static CurrentBenefitsResponse toDto(CurrentBenefitsVo value) {
        if (value == null) return null;
        return new CurrentBenefitsResponse(value.getPlanCode().name(), value.getDisplayName(), value.getPolicySnapshotId(), value.getBenefits().stream().map(SubscriptionModelMapper::toDto).toList());
    }

    public static QuotaRuleVo toVo(QuotaRuleDto value) {
        if (value == null) return null;
        return QuotaRuleVo.builder().quotaType(value.quotaType()).displayName(value.displayName()).limit(value.limit()).unit(value.unit()).period(QuotaPeriod.valueOf(value.period())).displayOrder(value.displayOrder()).build();
    }

    public static QuotaRuleDto toDto(QuotaRuleVo value) {
        if (value == null) return null;
        return new QuotaRuleDto(value.getQuotaType(), value.getDisplayName(), value.getLimit(), value.getUnit(), value.getPeriod().name(), value.getDisplayOrder());
    }

    public static QuotaPolicySnapshotVo toVo(QuotaPolicySnapshotDto value) {
        if (value == null) return null;
        return QuotaPolicySnapshotVo.builder().policySnapshotId(value.policySnapshotId()).policyScope(PolicyScope.valueOf(value.policyScope())).policyVersion(value.policyVersion()).rules(value.rules().stream().map(SubscriptionModelMapper::toVo).toList()).build();
    }

    public static QuotaPolicySnapshotDto toDto(QuotaPolicySnapshotVo value) {
        if (value == null) return null;
        return new QuotaPolicySnapshotDto(value.getPolicySnapshotId(), value.getPolicyScope().name(), value.getPolicyVersion(), value.getRules().stream().map(SubscriptionModelMapper::toDto).toList());
    }

    public static TrialActivationVo toVo(TrialActivationResponse value) {
        if (value == null) return null;
        return TrialActivationVo.builder().trialPeriodId(value.trialPeriodId()).periodStartAt(value.periodStartAt()).periodEndAt(value.periodEndAt()).policySnapshotId(value.policySnapshotId()).build();
    }

    public static TrialActivationResponse toDto(TrialActivationVo value) {
        if (value == null) return null;
        return new TrialActivationResponse(value.getTrialPeriodId(), value.getPeriodStartAt(), value.getPeriodEndAt(), value.getPolicySnapshotId());
    }

    public static PublicationResultVo toVo(PublicationResultResponse value) {
        if (value == null) return null;
        return PublicationResultVo.builder().publicationId(value.publicationId()).scope(PolicyScope.valueOf(value.scope())).version(value.version()).effectiveAt(value.effectiveAt()).acceptedAt(value.acceptedAt()).revision(value.revision()).build();
    }

    public static PublicationResultResponse toDto(PublicationResultVo value) {
        if (value == null) return null;
        return new PublicationResultResponse(value.getPublicationId(), value.getScope().name(), value.getVersion(), value.getEffectiveAt(), value.getAcceptedAt(), value.getRevision());
    }

    public static AdminConfigurationState adminConfigurationState(String id, long revision) {
        return AdminConfigurationState.builder().id(id).revision(revision).build();
    }

    public static AdminPublicationReceipt adminPublicationReceipt(String id, String actorId, String operation, String idempotencyKey, String fingerprint, Instant acceptedAt, PublicationResultResponse result) {
        return AdminPublicationReceipt.builder().id(id).actorId(actorId).operation(SubscriptionOperation.valueOf(operation)).idempotencyKey(idempotencyKey).fingerprint(fingerprint).acceptedAt(acceptedAt).result(toVo(result)).build();
    }

    public static PlanVersion planVersion(String id, String scope, long version, Instant effectiveAt, boolean published, int displayOrder, PlanCardResponse plan) {
        return PlanVersion.builder().id(id).scope(PolicyScope.valueOf(scope)).version(version).effectiveAt(effectiveAt).published(published).displayOrder(displayOrder).plan(toVo(plan)).build();
    }

    public static PolicyState policyState(String id, String activeVersionId, String pendingVersionId, Instant pendingEffectiveAt, long activationSequence, long revision, String manifestFingerprint) {
        return PolicyState.builder().id(id).activeVersionId(activeVersionId).pendingVersionId(pendingVersionId).pendingEffectiveAt(pendingEffectiveAt).activationSequence(activationSequence).revision(revision).manifestFingerprint(manifestFingerprint).build();
    }

    public static PolicyVersion policyVersion(String id, String scope, long version, Instant effectiveAt, QuotaPolicySnapshotDto snapshot, CurrentBenefitsResponse benefits) {
        return PolicyVersion.builder().id(id).scope(PolicyScope.valueOf(scope)).version(version).effectiveAt(effectiveAt).snapshot(toVo(snapshot)).benefits(toVo(benefits)).build();
    }

    public static PublicationReceipt publicationReceipt(String id, String fingerprint, String environment) {
        return PublicationReceipt.builder().id(id).fingerprint(fingerprint).environment(environment).build();
    }

    public static SubscriptionAccount subscriptionAccount(String id, boolean trialUsed, long entitlementVersion, long revision, String provenance) {
        return SubscriptionAccount.builder().id(id).trialUsed(trialUsed).entitlementVersion(entitlementVersion).revision(revision).provenance(AccountProvenance.valueOf(provenance)).build();
    }

    public static SubscriptionPeriod subscriptionPeriod(String id, String userId, String kind, String state, Instant periodStartAt, Instant periodEndAt, QuotaPolicySnapshotDto snapshot, CurrentBenefitsResponse benefits, List<PlanBillingOptionResponse> prices) {
        return SubscriptionPeriod.builder().id(id).userId(userId).kind(PeriodKind.valueOf(kind)).state(PeriodState.valueOf(state)).periodStartAt(periodStartAt).periodEndAt(periodEndAt).snapshot(toVo(snapshot)).benefits(toVo(benefits)).prices(prices.stream().map(SubscriptionModelMapper::toVo).toList()).build();
    }

    public static TrialReceipt trialReceipt(String id, String userId, String operation, String idempotencyKey, String fingerprint, TrialActivationResponse result) {
        return TrialReceipt.builder().id(id).userId(userId).operation(SubscriptionOperation.valueOf(operation)).idempotencyKey(idempotencyKey).fingerprint(fingerprint).result(toVo(result)).build();
    }
}
