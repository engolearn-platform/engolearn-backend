package fit.iuh.engolearn.subscription.service.impl;

import com.mongodb.MongoException;
import fit.iuh.engolearn.subscription.dto.request.*;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.ConfigurationContentRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBenefitRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBillingOptionRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.QuotaRuleRequest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionProductRepository;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import fit.iuh.engolearn.subscription.service.SubscriptionProductService;
import fit.iuh.engolearn.subscription.service.SubscriptionTransactions;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SubscriptionProductServiceImpl implements SubscriptionProductService {
    private static final long MAX = 9_007_199_254_740_991L;
    private static final String LEGACY_PREMIUM = "PREMIUM";
    private final SubscriptionProductRepository products;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionTransactions transactions;
    private final Clock clock;
    private final ObjectMapper mapper;

    private SubscriptionRequestException error(int status, String code, String detail) {
        return new SubscriptionRequestException(status, code, detail);
    }

    private void check(boolean condition, int status, String code, String detail) {
        if (!condition) throw error(status, code, detail);
    }

    private SubscriptionProduct product(String code) {
        SubscriptionProduct found = products.findProduct(code);
        if (found == null && LEGACY_PREMIUM.equals(code)) found = migrateLegacyPremium();
        if (found != null && LEGACY_PREMIUM.equals(code)) found = applyDueLegacySchedule(found);
        return found;
    }

    private SubscriptionProduct migrateLegacyPremium() {
        Instant at = clock.instant();
        List<PolicyVersion> policies = subscriptions.findPolicyVersions().stream()
                .filter(value -> value.getScope() == PolicyScope.PREMIUM)
                .toList();
        List<PlanVersion> legacy = subscriptions.findPlanVersions().stream()
                .filter(value -> value.isPublished() && value.getScope() == PolicyScope.PREMIUM
                        && LEGACY_PREMIUM.equals(value.getPlan().getPlanCode()))
                .toList();
        Map<Long, PolicyVersion> policiesByVersion = new HashMap<>();
        for (PolicyVersion policy : policies) policiesByVersion.put(policy.getVersion(), policy);
        PolicyState state = subscriptions.findPolicyState("PREMIUM");
        List<SubscriptionProductVersion> history = new ArrayList<>();
        for (PlanVersion plan : legacy) {
            PolicyVersion policy = policiesByVersion.get(plan.getVersion());
            if (policy == null || !plan.getEffectiveAt().equals(policy.getEffectiveAt())) continue;
            ProductContentVo content = legacyContent(plan, policy);
            String scheduleStatus = state != null && Objects.equals(state.getPendingVersionId(), policy.getId()) ? "SCHEDULED"
                    : state != null && Objects.equals(state.getActiveVersionId(), policy.getId())
                        && plan.getVersion() > 1 && !policy.getEffectiveAt().isAfter(at) ? "APPLIED"
                    : policy.getEffectiveAt().isAfter(at) ? "SUPERSEDED" : null;
            history.add(SubscriptionProductVersion.builder().id(plan.getId()).planCode(LEGACY_PREMIUM)
                    .version(plan.getVersion()).effectiveAt(plan.getEffectiveAt()).managed(false).content(content)
                    .legacyScheduleStatus(scheduleStatus).build());
        }
        String activeId = state == null ? null : state.getActiveVersionId();
        SubscriptionProductVersion current = history.stream().filter(value -> Objects.equals(value.getId(), activeId))
                .findFirst().orElseGet(() -> history.stream().filter(value -> !value.getEffectiveAt().isAfter(at))
                        .max(Comparator.comparing(SubscriptionProductVersion::getEffectiveAt)
                                .thenComparingLong(SubscriptionProductVersion::getVersion)).orElse(null));
        SubscriptionProductVersion scheduled = history.stream()
                .filter(value -> "SCHEDULED".equals(value.getLegacyScheduleStatus())
                        || "APPLIED".equals(value.getLegacyScheduleStatus()))
                .max(Comparator.comparingLong(SubscriptionProductVersion::getVersion)).orElse(null);
        if (current == null && scheduled == null) return null;
        for (SubscriptionProductVersion version : history) products.insertVersion(version);
        return products.insertProduct(SubscriptionProduct.builder().id(LEGACY_PREMIUM).planCode(LEGACY_PREMIUM)
                .visibility(ProductVisibility.VISIBLE).saleStatus(ProductSaleStatus.ON_SALE).archived(false)
                .currentVersion(current == null ? null : current.getVersion()).draftRevision(0).draftContent(null)
                .legacyScheduleVersion(scheduled == null ? null : scheduled.getVersion()).purchaseFence(0).build());
    }

    private SubscriptionProduct applyDueLegacySchedule(SubscriptionProduct product) {
        if (product.getLegacyScheduleVersion() == null) return product;
        PolicyState state = subscriptions.findPolicyState("PREMIUM");
        if (state == null) return product;
        PolicyVersion pending;
        boolean pendingActivation = state.getPendingVersionId() != null;
        if (pendingActivation) {
            if (clock.instant().isBefore(state.getPendingEffectiveAt())) return product;
            pending = subscriptions.findPolicyVersion(state.getPendingVersionId());
        } else {
            SubscriptionProductVersion scheduledVersion = products.findVersion(LEGACY_PREMIUM,
                    product.getLegacyScheduleVersion());
            if (scheduledVersion == null || !"SCHEDULED".equals(scheduledVersion.getLegacyScheduleStatus())) return product;
            pending = subscriptions.findPolicyVersion(state.getActiveVersionId());
            if (pending == null || !Objects.equals(scheduledVersion.getVersion(), pending.getVersion())
                    || clock.instant().isBefore(pending.getEffectiveAt())) return product;
        }
        if (pending == null) throw new IllegalStateException("Scheduled Premium policy is missing");
        PlanVersion scheduledPlan = subscriptions.findPlanVersions().stream()
                .filter(value -> value.getScope() == PolicyScope.PREMIUM && value.getVersion() == pending.getVersion()
                        && value.isPublished() && value.getEffectiveAt().equals(pending.getEffectiveAt()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Scheduled Premium offer is missing"));
        if (!Objects.equals(product.getLegacyScheduleVersion(), scheduledPlan.getVersion())) return product;
        if (pendingActivation) {
            if (!subscriptions.advanceConfigurationRevision(subscriptions.readConfigurationRevision()))
                throw new OptimisticLockingFailureException("Configuration revision changed");
            if (!subscriptions.activatePendingPolicy(state))
                throw new OptimisticLockingFailureException("Legacy Premium schedule changed");
        }
        if (!products.applyLegacySchedule(LEGACY_PREMIUM, product.getCurrentVersion(), scheduledPlan.getVersion())
                || !products.setLegacyScheduleStatus(LEGACY_PREMIUM, scheduledPlan.getVersion(), "APPLIED"))
            throw new OptimisticLockingFailureException("Legacy Premium schedule changed");
        return products.findProduct(LEGACY_PREMIUM);
    }

    private ProductContentVo legacyContent(PlanVersion plan, PolicyVersion policy) {
        PlanCardVo card = plan.getPlan();
        return ProductContentVo.builder().displayName(card.getDisplayName()).description(card.getDescription())
                .displayOrder(plan.getDisplayOrder()).benefits(card.getBenefits())
                .billingOptions(card.getBillingOptions()).quotaRules(policy.getSnapshot().getRules()).build();
    }

    private <T> T read(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return action.get();
        try {
            subscriptions.ensurePublicationStorage();
            return transactions.execute(action);
        } catch (SubscriptionRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Published product configuration cannot be confirmed");
        }
    }

    private <T> T write(Supplier<T> action) {
        try {
            subscriptions.ensurePublicationStorage();
            return transactions.execute(action);
        } catch (SubscriptionRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product configuration cannot be confirmed");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductAdminListResponse adminList() {
        return read(() -> {
            product(LEGACY_PREMIUM);
            return new SubscriptionProductAdminListResponse(products.findProducts().stream()
                    .map(this::summary).toList(), SubscriptionProductRegistry.services());
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse adminDetail(String planCode) {
        return read(() -> detail(productOrNotFound(planCode)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductVersionResponse versionDetail(String planCode, long version) {
        return read(() -> {
            SubscriptionProduct product = product(planCode);
            if (product == null) throw error(404, "PLAN_NOT_FOUND", "Paid product was not found");
            SubscriptionProductVersion found = products.findVersion(planCode, version);
            if (found == null) throw error(404, "PLAN_VERSION_NOT_FOUND", "Product version was not found");
            return versionResponse(found);
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse create(String planCode) {
        return write(() -> {
            check(validCode(planCode) && !Set.of("FREE", "TRIAL", LEGACY_PREMIUM).contains(planCode), 400, "INVALID_REQUEST",
                    "A valid paid product code is required");
            if (product(planCode) != null) throw error(409, "PLAN_CODE_EXISTS", "Product code is already in use");
            SubscriptionProduct created = products.insertProduct(SubscriptionProduct.builder().id(planCode)
                    .planCode(planCode).visibility(ProductVisibility.VISIBLE).saleStatus(ProductSaleStatus.ON_SALE)
                    .archived(false).draftRevision(0).purchaseFence(0).build());
            return detail(created);
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse saveDraft(String planCode, SaveSubscriptionProductDraftRequest request) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            requireNotArchived(current);
            check(current.getDraftRevision() == request.expectedDraftRevision(), 409, "DRAFT_REVISION_STALE",
                    "Draft changed; reload it before saving");
            ProductContentVo normalized = normalizeDraft(request.content());
            validateSupported(normalized, false);
            if (!products.saveDraft(planCode, request.expectedDraftRevision(), normalized))
                throw error(409, "DRAFT_REVISION_STALE", "Draft changed; reload it before saving");
            return detail(productOrNotFound(planCode));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductPreviewResponse preview(String planCode, PreviewSubscriptionProductRequest request) {
        return read(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            requireNotArchived(current);
            check(current.getDraftRevision() == request.expectedDraftRevision(), 409, "DRAFT_REVISION_STALE",
                    "Draft changed; reload it before previewing");
            ProductContentVo content = current.getDraftContent();
            validatePublishable(content);
            return new SubscriptionProductPreviewResponse(toRequest(content), clock.instant(), changedQuotaTypes(
                    current.getCurrentVersion() == null ? null : products.findVersion(planCode, current.getCurrentVersion()),
                    content));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse publish(String actor, String key, String planCode,
            PublishSubscriptionProductRequest request) {
        String normalizedKey = uuidKey(key);
        String fingerprint = fingerprint(planCode, request.expectedDraftRevision(), request.replaceScheduled(),
                request.expectedScheduleVersion(), request.expectedScheduleEffectiveAt());
        subscriptions.ensurePublicationStorage();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(() -> {
                    SubscriptionProductPublicationReceipt receipt = products.findPublicationReceipt(actor, normalizedKey);
                    if (receipt != null) {
                        check(receipt.getPlanCode().equals(planCode) && receipt.getFingerprint().equals(fingerprint),
                                409, "IDEMPOTENCY_CONFLICT", "Intent key was already used for another publication");
                        return detail(productOrNotFound(planCode), receipt.getVersion());
                    }
                    SubscriptionProduct current = productOrNotFound(planCode);
                    requireNotArchived(current);
                    check(current.getDraftRevision() == request.expectedDraftRevision(), 409, "DRAFT_REVISION_STALE",
                            "Draft changed; reload it before publishing");
                    ProductContentVo content = current.getDraftContent();
                    validatePublishable(content);
                    long highestKnownVersion = products.findVersions(planCode).stream()
                            .mapToLong(SubscriptionProductVersion::getVersion).max()
                            .orElse(current.getCurrentVersion() == null ? 0 : current.getCurrentVersion());
                    long versionNumber = highestKnownVersion + 1;
                    check(versionNumber <= MAX, 409, "PLAN_VERSION_EXHAUSTED", "Product version range exhausted");
                    Instant acceptedAt = clock.instant();
                    SubscriptionProductVersion version = SubscriptionProductVersion.builder().id(UUID.randomUUID().toString())
                            .planCode(planCode).version(versionNumber).effectiveAt(acceptedAt).managed(true).content(content).build();
                    Long scheduleVersion = current.getLegacyScheduleVersion();
                    if (LEGACY_PREMIUM.equals(planCode) && scheduleVersion != null) {
                        SubscriptionProductVersion scheduled = products.findVersion(planCode, scheduleVersion);
                        if (scheduled != null && "SCHEDULED".equals(scheduled.getLegacyScheduleStatus())) {
                            check(Boolean.TRUE.equals(request.replaceScheduled())
                                            && Objects.equals(request.expectedScheduleVersion(), scheduleVersion)
                                            && Objects.equals(request.expectedScheduleEffectiveAt(), scheduled.getEffectiveAt()), 409,
                                    "LEGACY_SCHEDULE_REPLACEMENT_REQUIRED",
                                    "The scheduled Premium offer changed; reload it and confirm replacement");
                            PolicyState policyState = subscriptions.findPolicyState("PREMIUM");
                            check(policyState != null && policyState.getPendingVersionId() != null
                                            && clock.instant().isBefore(policyState.getPendingEffectiveAt()), 409,
                                    "LEGACY_SCHEDULE_ALREADY_DUE", "The scheduled Premium offer can no longer be replaced");
                            if (policyState.getPendingVersionId() != null) {
                                check(subscriptions.advanceConfigurationRevision(subscriptions.readConfigurationRevision()),
                                        409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before publishing");
                                ConfigurationContentRequest compatibility = legacyConfigurationContent(content);
                                PolicyVersion policy = SubscriptionModelMapper.policyVersion(version.getId(), "PREMIUM",
                                        versionNumber, acceptedAt, planCode, compatibility);
                                PlanVersion plan = SubscriptionModelMapper.planVersion(version.getId(), "PREMIUM",
                                        versionNumber, acceptedAt, planCode, compatibility);
                                subscriptions.publishPolicyVersion(policy);
                                subscriptions.publishPlanVersion(plan);
                                if (!subscriptions.replacePendingWithActive(policyState, policy))
                                    throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before publishing");
                                if (!products.fenceLegacySchedule(planCode, current.getCurrentVersion(), scheduleVersion))
                                    throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before publishing");
                                if (!products.setLegacyScheduleStatus(planCode, scheduleVersion, "SUPERSEDED"))
                                    throw new IllegalStateException("Scheduled Premium version is missing");
                            }
                        }
                    }
                    if (!products.publish(planCode, current.getCurrentVersion(), current.getDraftRevision(), versionNumber))
                        throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before publishing");
                    products.insertVersion(version);
                    products.insertPublicationReceipt(SubscriptionProductPublicationReceipt.builder()
                            .id(UUID.randomUUID().toString()).actorId(actor).idempotencyKey(normalizedKey)
                            .planCode(planCode).fingerprint(fingerprint).version(versionNumber).acceptedAt(acceptedAt).build());
                    return detail(productOrNotFound(planCode), versionNumber);
                });
            } catch (SubscriptionRequestException ex) {
                throw ex;
            } catch (RuntimeException failure) {
                if (unknownCommit(failure)) {
                    SubscriptionProductPublicationReceipt receipt = publicationReceipt(actor, normalizedKey);
                    if (receipt != null) {
                        check(receipt.getPlanCode().equals(planCode) && receipt.getFingerprint().equals(fingerprint),
                                409, "IDEMPOTENCY_CONFLICT", "Intent key was already used for another publication");
                        return read(() -> detail(productOrNotFound(planCode), receipt.getVersion()));
                    }
                    throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product publication cannot be confirmed");
                }
                if (!retryable(failure) || attempt == 2)
                    throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product publication cannot be confirmed");
            }
        }
        throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product publication cannot be confirmed");
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse rescheduleLegacyPremium(RescheduleLegacyPremiumRequest request) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(LEGACY_PREMIUM);
            Long scheduleVersion = current.getLegacyScheduleVersion();
            check(scheduleVersion != null && Objects.equals(scheduleVersion, request.expectedVersion()), 409,
                    "LEGACY_SCHEDULE_STALE", "Scheduled Premium offer changed; reload it before rescheduling");
            SubscriptionProductVersion scheduled = products.findVersion(LEGACY_PREMIUM, scheduleVersion);
            check(scheduled != null && Objects.equals(scheduled.getEffectiveAt(), request.expectedEffectiveAt())
                            && "SCHEDULED".equals(scheduled.getLegacyScheduleStatus()), 409,
                    "LEGACY_SCHEDULE_STALE", "Scheduled Premium offer changed; reload it before rescheduling");
            check(request.effectiveAt().isAfter(clock.instant()), 400, "INVALID_REQUEST",
                    "Scheduled activation must be in the future");
            PolicyState state = subscriptions.findPolicyState("PREMIUM");
            check(state != null, 409, "LEGACY_SCHEDULE_UNAVAILABLE", "Legacy Premium policy is unavailable");
            boolean replacing = state.getPendingVersionId() != null;
            if (replacing) {
                check(Objects.equals(state.getPendingEffectiveAt(), request.expectedEffectiveAt())
                                && clock.instant().isBefore(state.getPendingEffectiveAt()), 409,
                        "LEGACY_SCHEDULE_ALREADY_DUE", "A due Premium schedule can no longer be changed");
                PolicyVersion pending = subscriptions.findPolicyVersion(state.getPendingVersionId());
                check(pending != null && pending.getVersion() == scheduleVersion, 409, "LEGACY_SCHEDULE_STALE",
                        "Scheduled Premium offer changed; reload it before rescheduling");
            }
            check(subscriptions.advanceConfigurationRevision(subscriptions.readConfigurationRevision()), 409,
                    "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before rescheduling");
            long nextVersion = products.findVersions(LEGACY_PREMIUM).stream().mapToLong(SubscriptionProductVersion::getVersion)
                    .max().orElse(0) + 1;
            String id = UUID.randomUUID().toString();
            SubscriptionProductVersion next = SubscriptionProductVersion.builder().id(id).planCode(LEGACY_PREMIUM)
                    .version(nextVersion).effectiveAt(request.effectiveAt()).managed(false)
                    .content(scheduled.getContent()).legacyScheduleStatus("SCHEDULED").build();
            ConfigurationContentRequest content = legacyConfigurationContent(next.getContent());
            PolicyVersion policy = SubscriptionModelMapper.policyVersion(id, "PREMIUM", nextVersion,
                    request.effectiveAt(), LEGACY_PREMIUM, content);
            PlanVersion plan = SubscriptionModelMapper.planVersion(id, "PREMIUM", nextVersion,
                    request.effectiveAt(), LEGACY_PREMIUM, content);
            subscriptions.publishPolicyVersion(policy);
            subscriptions.publishPlanVersion(plan);
            boolean updated = replacing ? subscriptions.replacePendingPolicy(state, policy)
                    : subscriptions.schedulePolicy(state, policy);
            if (!updated || !products.replaceLegacySchedule(LEGACY_PREMIUM, current.getCurrentVersion(), scheduleVersion,
                    nextVersion)) throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before rescheduling");
            products.setLegacyScheduleStatus(LEGACY_PREMIUM, scheduleVersion, "SUPERSEDED");
            products.insertVersion(next);
            return detail(productOrNotFound(LEGACY_PREMIUM));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse cancelLegacyPremiumSchedule(CancelLegacyPremiumScheduleRequest request) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(LEGACY_PREMIUM);
            Long scheduleVersion = current.getLegacyScheduleVersion();
            check(scheduleVersion != null && Objects.equals(scheduleVersion, request.expectedVersion()), 409,
                    "LEGACY_SCHEDULE_STALE", "Scheduled Premium offer changed; reload it before cancelling");
            SubscriptionProductVersion scheduled = products.findVersion(LEGACY_PREMIUM, scheduleVersion);
            PolicyState state = subscriptions.findPolicyState("PREMIUM");
            check(scheduled != null && state != null && "SCHEDULED".equals(scheduled.getLegacyScheduleStatus())
                            && Objects.equals(scheduled.getEffectiveAt(), request.expectedEffectiveAt())
                            && Objects.equals(state.getPendingEffectiveAt(), request.expectedEffectiveAt()),
                    409, "LEGACY_SCHEDULE_STALE", "Scheduled Premium offer changed; reload it before cancelling");
            check(clock.instant().isBefore(state.getPendingEffectiveAt()), 409, "LEGACY_SCHEDULE_ALREADY_DUE",
                    "A due Premium schedule can no longer be cancelled");
            check(subscriptions.advanceConfigurationRevision(subscriptions.readConfigurationRevision()), 409,
                    "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before cancelling");
            if (!subscriptions.cancelPendingPolicy(state)
                    || !products.fenceLegacySchedule(LEGACY_PREMIUM, current.getCurrentVersion(), scheduleVersion))
                throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before cancelling");
            products.setLegacyScheduleStatus(LEGACY_PREMIUM, scheduleVersion, "CANCELLED");
            return detail(productOrNotFound(LEGACY_PREMIUM));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse visibility(String planCode, SetProductVisibilityRequest request) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            requireNotArchived(current);
            boolean hidden = "HIDDEN".equals(request.visibility());
            if (!products.setVisibility(planCode, hidden)) throw error(409, "PLAN_ARCHIVED", "Archived product cannot change");
            return detail(productOrNotFound(planCode));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse saleStatus(String planCode, SetProductSaleStatusRequest request) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            requireNotArchived(current);
            boolean stopped = "STOPPED".equals(request.saleStatus());
            if (!products.setSaleStatus(planCode, stopped)) throw error(409, "PLAN_ARCHIVED", "Archived product cannot change");
            return detail(productOrNotFound(planCode));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionProductDetailResponse archive(String planCode) {
        return write(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            if (!current.isArchived() && !products.archive(planCode))
                throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before archiving");
            return detail(productOrNotFound(planCode));
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDraft(String planCode) {
        write(() -> {
            SubscriptionProduct current = productOrNotFound(planCode);
            requireNotArchived(current);
            check(current.getCurrentVersion() == null, 409, "PUBLISHED_PRODUCT_CANNOT_DELETE",
                    "A published product cannot be deleted");
            check(current.getLegacyScheduleVersion() == null && products.findVersions(planCode).isEmpty(), 409,
                    "PUBLISHED_PRODUCT_CANNOT_DELETE", "A product with legacy publication history cannot be deleted");
            if (!products.deleteDraft(planCode, current.getDraftRevision()))
                throw error(409, "PRODUCT_CONCURRENT_UPDATE", "Product changed; reload it before deleting");
            return null;
        });
    }

    public List<PlanCardResponse> publicPlans() {
        return read(() -> {
            product(LEGACY_PREMIUM);
            Instant at = clock.instant();
            Map<String, PlanVersion> free = new HashMap<>();
            for (PlanVersion value : subscriptions.findPublishedPlans(at)) {
                if (value.getScope() == PolicyScope.FREE && value.getPlan() != null)
                    free.putIfAbsent(value.getPlan().getPlanCode(), value);
            }
            record OrderedCard(int order, PlanCardResponse card) {}
            List<OrderedCard> result = new ArrayList<>();
            free.values().stream().sorted(Comparator.comparingInt(PlanVersion::getDisplayOrder))
                    .map(value -> new OrderedCard(value.getDisplayOrder(),
                            SubscriptionModelMapper.sortedPlanCard(value.getPlan())))
                    .forEach(result::add);
            for (SubscriptionProduct product : products.findProducts()) {
                if (product.getVisibility() != ProductVisibility.VISIBLE || product.isArchived() || product.getCurrentVersion() == null)
                    continue;
                SubscriptionProductVersion version = products.findVersion(product.getPlanCode(), product.getCurrentVersion());
                if (version == null) throw new IllegalStateException("Published product version is missing");
                result.add(new OrderedCard(version.getContent().getDisplayOrder(), card(product, version)));
            }
            return result.stream().sorted(Comparator.comparingInt(OrderedCard::order).thenComparing(
                            item -> item.card().planCode()))
                    .map(OrderedCard::card).toList();
        });
    }

    public PlanCardResponse publicPlan(String planCode) {
        return read(() -> {
            SubscriptionProduct product = product(planCode);
            if (product == null || product.isArchived() || product.getCurrentVersion() == null)
                throw error(404, "PLAN_NOT_FOUND", "Paid product was not found");
            SubscriptionProductVersion version = products.findVersion(planCode, product.getCurrentVersion());
            if (version == null) throw new IllegalStateException("Published product version is missing");
            return card(product, version);
        });
    }

    public PurchaseSelection reserveForPurchase(String planCode, long expectedVersion, String billingPeriod) {
        Supplier<PurchaseSelection> action = () -> {
            SubscriptionProduct product = product(planCode);
            if (product == null)
                throw error(409, "OFFER_UNAVAILABLE", "Paid product is not currently offered");
            requireSaleEligible(product, expectedVersion);
            if (product.getCurrentVersion() == null)
                throw error(409, "OFFER_UNAVAILABLE", "Paid product is not currently offered");
            SubscriptionProductVersion version = products.findVersion(planCode, product.getCurrentVersion());
            if (version == null) throw new IllegalStateException("Published product version is missing");
            PlanBillingOptionVo option = version.getContent().getBillingOptions().stream()
                    .filter(value -> value.getBillingPeriod().name().equals(billingPeriod)).findFirst()
                    .orElseThrow(() -> error(409, "OFFER_UNAVAILABLE", "Billing option is unavailable"));
            if (!products.reservePurchase(planCode, expectedVersion)) {
                SubscriptionProduct refreshed = product(planCode);
                if (refreshed == null)
                    throw error(409, "OFFER_UNAVAILABLE", "Paid product is not currently offered");
                requireSaleEligible(refreshed, expectedVersion);
                if (refreshed.getCurrentVersion() == null)
                    throw error(409, "OFFER_UNAVAILABLE", "Paid product is not currently offered");
                throw error(409, "PLAN_VERSION_STALE", "Product changed; review the current offer before purchasing");
            }
            return selection(product, version, option);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) return action.get();
        return read(action);
    }

    private PurchaseSelection selection(SubscriptionProduct product, SubscriptionProductVersion version,
            PlanBillingOptionVo option) {
        String snapshotId = version.getId();
        ProductContentVo content = version.getContent();
        CurrentBenefitsVo benefits = CurrentBenefitsVo.builder().planCode(product.getPlanCode())
                .displayName(content.getDisplayName()).policySnapshotId(snapshotId).managedProduct(version.isManaged())
                .benefits(content.getBenefits()).build();
        QuotaPolicySnapshotVo snapshot = QuotaPolicySnapshotVo.builder().policySnapshotId(snapshotId)
                .policyScope(PolicyScope.PREMIUM).policyVersion(version.getVersion())
                .rules(content.getQuotaRules()).build();
        return new PurchaseSelection(card(product, version), benefits, snapshot, option,
                version.getVersion(), version.getEffectiveAt());
    }

    private void requireSaleEligible(SubscriptionProduct product, long expectedVersion) {
        if (product.isArchived()) throw error(409, "PLAN_ARCHIVED", "Product has been archived");
        if (product.getSaleStatus() == ProductSaleStatus.STOPPED)
            throw error(409, "PLAN_SALE_STOPPED", "Product sales are stopped");
        if (!Objects.equals(product.getCurrentVersion(), expectedVersion))
            throw error(409, "PLAN_VERSION_STALE", "Product changed; review the current offer before purchasing");
    }

    private SubscriptionProduct productOrNotFound(String code) {
        check(validCode(code), 400, "INVALID_REQUEST", "Product code is invalid");
        SubscriptionProduct product = product(code);
        if (product == null) throw error(404, "PLAN_NOT_FOUND", "Paid product was not found");
        return product;
    }

    private void requireNotArchived(SubscriptionProduct product) {
        if (product.isArchived()) throw error(409, "PLAN_ARCHIVED", "Product has been archived");
    }

    private SubscriptionProductSummaryResponse summary(SubscriptionProduct product) {
        SubscriptionProductVersion version = product.getCurrentVersion() == null ? null
                : products.findVersion(product.getPlanCode(), product.getCurrentVersion());
        String displayName = version != null ? version.getContent().getDisplayName()
                : product.getDraftContent() == null ? null : product.getDraftContent().getDisplayName();
        return new SubscriptionProductSummaryResponse(product.getPlanCode(), product.getVisibility().name(),
                product.getSaleStatus().name(), product.isArchived(), product.getCurrentVersion(),
                product.getDraftRevision(), hasDraft(product), displayName);
    }

    private SubscriptionProductDetailResponse detail(SubscriptionProduct product) {
        return detail(product, null);
    }

    private SubscriptionProductDetailResponse detail(SubscriptionProduct product, Long publishedVersion) {
        SubscriptionProductVersion current = product.getCurrentVersion() == null ? null
                : products.findVersion(product.getPlanCode(), product.getCurrentVersion());
        if (product.getCurrentVersion() != null && current == null) throw new IllegalStateException("Published product version is missing");
        List<SubscriptionProductVersionSummaryResponse> history = products.findVersions(product.getPlanCode()).stream()
                .map(value -> new SubscriptionProductVersionSummaryResponse(value.getVersion(), value.getEffectiveAt(),
                        value.getLegacyScheduleStatus())).toList();
        SubscriptionProductVersion accepted = publishedVersion == null ? null
                : products.findVersion(product.getPlanCode(), publishedVersion);
        if (publishedVersion != null && accepted == null) throw new IllegalStateException("Accepted product version is missing");
        return new SubscriptionProductDetailResponse(product.getPlanCode(), product.getVisibility().name(),
                product.getSaleStatus().name(), product.isArchived(), product.getCurrentVersion(), product.getDraftRevision(),
                hasDraft(product),
                current != null ? current.getContent().getDisplayName()
                        : product.getDraftContent() == null ? null : product.getDraftContent().getDisplayName(),
                current == null ? null : versionResponse(current),
                new SubscriptionProductDraftResponse(product.getDraftRevision(),
                        product.getDraftContent() == null ? null : toRequest(product.getDraftContent())), history,
                accepted == null ? null : versionResponse(accepted), legacySchedule(product));
    }

    private SubscriptionProductVersionResponse versionResponse(SubscriptionProductVersion value) {
        return new SubscriptionProductVersionResponse(value.getVersion(), value.getEffectiveAt(),
                toRequest(value.getContent()), value.getLegacyScheduleStatus());
    }

    private ConfigurationContentRequest legacyConfigurationContent(ProductContentVo value) {
        SubscriptionProductContentRequest request = toRequest(value);
        return new ConfigurationContentRequest(request.displayName(), request.description(), request.displayOrder(),
                request.benefits(), request.billingOptions(), request.quotaRules());
    }

    private LegacyScheduleResponse legacySchedule(SubscriptionProduct product) {
        if (!LEGACY_PREMIUM.equals(product.getPlanCode()) || product.getLegacyScheduleVersion() == null) return null;
        SubscriptionProductVersion version = products.findVersion(product.getPlanCode(), product.getLegacyScheduleVersion());
        if (version == null || version.getLegacyScheduleStatus() == null) return null;
        return new LegacyScheduleResponse(version.getVersion(), version.getEffectiveAt(), version.getLegacyScheduleStatus());
    }

    private boolean hasDraft(SubscriptionProduct product) {
        return product.getDraftContent() != null
                || product.getCurrentVersion() == null && product.getLegacyScheduleVersion() == null;
    }

    private PlanCardResponse card(SubscriptionProduct product, SubscriptionProductVersion version) {
        ProductContentVo content = version.getContent();
        return new PlanCardResponse(product.getPlanCode(), content.getDisplayName(), content.getDescription(),
                content.getBenefits().stream().sorted(Comparator.comparingInt(PlanBenefitVo::getSortOrder)
                        .thenComparing(PlanBenefitVo::getCode)).map(SubscriptionModelMapper::toDto).toList(),
                content.getBillingOptions().stream().map(SubscriptionModelMapper::toDto).toList(),
                version.getVersion(), product.getSaleStatus().name());
    }

    private ProductContentVo normalizeDraft(SubscriptionProductContentRequest content) {
        check(content != null && content.benefits() != null && content.billingOptions() != null && content.quotaRules() != null,
                400, "INVALID_REQUEST", "Draft content is incomplete");
        return ProductContentVo.builder().displayName(content.displayName().trim())
                .description(content.description() == null ? null : content.description().trim())
                .displayOrder(content.displayOrder()).benefits(content.benefits().stream()
                        .map(value -> PlanBenefitVo.builder().code(value.code().trim()).title(value.title().trim())
                                .description(value.description().trim()).displayValue(value.displayValue())
                                .sortOrder(value.sortOrder()).build())
                        .sorted(Comparator.comparing(PlanBenefitVo::getCode)).toList())
                .billingOptions(content.billingOptions().stream().map(value -> PlanBillingOptionVo.builder()
                                .billingPeriod(BillingPeriod.valueOf(value.billingPeriod())).amount(value.amount())
                                .currency(value.currency()).build())
                        .sorted(Comparator.comparing(value -> value.getBillingPeriod().name())).toList())
                .quotaRules(content.quotaRules().stream().map(this::quotaRule)
                        .sorted(Comparator.comparing(QuotaRuleVo::getQuotaType)).toList()).build();
    }

    private QuotaRuleVo quotaRule(QuotaRuleRequest value) {
        return QuotaRuleVo.builder().quotaType(value.quotaType().trim()).displayName(value.displayName().trim())
                .limit(value.limit()).unit(value.unit().trim()).period(QuotaPeriod.valueOf(value.period()))
                .displayOrder(value.displayOrder()).build();
    }

    private SubscriptionProductContentRequest toRequest(ProductContentVo content) {
        return new SubscriptionProductContentRequest(content.getDisplayName(), content.getDescription(), content.getDisplayOrder(),
                content.getBenefits().stream().map(SubscriptionModelMapper::toDto)
                        .map(value -> new PlanBenefitRequest(value.code(), value.title(), value.description(),
                                value.displayValue(), value.sortOrder())).toList(),
                content.getBillingOptions().stream().map(SubscriptionModelMapper::toDto)
                        .map(value -> new PlanBillingOptionRequest(value.billingPeriod(), value.amount(), value.currency())).toList(),
                content.getQuotaRules().stream().map(value -> new QuotaRuleRequest(value.getQuotaType(), value.getDisplayName(),
                        value.getUnit(), value.getPeriod().name(), value.getLimit(), value.getDisplayOrder())).toList());
    }

    private void validateSupported(ProductContentVo content, boolean requireComplete) {
        check(content != null, 400, "INVALID_REQUEST", "Draft content is required");
        Set<String> benefitCodes = new HashSet<>();
        for (PlanBenefitVo benefit : content.getBenefits()) {
            check(SubscriptionProductRegistry.service(benefit.getCode()) != null, 400, "INVALID_REQUEST",
                    "Benefit code is not supported");
            check(benefitCodes.add(benefit.getCode()), 400, "INVALID_REQUEST", "Benefit codes must be unique");
        }
        Map<String, QuotaRuleVo> rules = new HashMap<>();
        for (QuotaRuleVo rule : content.getQuotaRules()) {
            SubscriptionProductServiceResponse service = SubscriptionProductRegistry.services().stream()
                    .filter(value -> Objects.equals(value.quotaType(), rule.getQuotaType())).findFirst().orElse(null);
            check(service != null && benefitCodes.contains(service.code()), 400, "INVALID_REQUEST",
                    "Quota rules must belong to a selected metered service");
            check(service.unit().equals(rule.getUnit()) && service.period().equals(rule.getPeriod().name()),
                    409, "CANONICAL_QUOTA_CONFLICT", "Quota unit and period must match the supported service");
            check(rules.putIfAbsent(rule.getQuotaType(), rule) == null, 400, "INVALID_REQUEST", "Quota types must be unique");
            for (PolicyVersion policy : subscriptions.findPolicyVersions()) {
                QuotaRuleVo prior = policy.getSnapshot().getRules().stream()
                        .filter(value -> value.getQuotaType().equals(rule.getQuotaType())).findFirst().orElse(null);
                check(prior == null || prior.getUnit().equals(rule.getUnit()) && prior.getPeriod() == rule.getPeriod(),
                        409, "CANONICAL_QUOTA_CONFLICT", "Known quota unit and period cannot change");
            }
        }
        if (requireComplete) {
            check(!benefitCodes.isEmpty(), 400, "INVALID_REQUEST", "At least one supported service is required");
            for (String code : benefitCodes) {
                SubscriptionProductServiceResponse service = SubscriptionProductRegistry.service(code);
                if (service.quotaType() != null)
                    check(rules.containsKey(service.quotaType()), 400, "INVALID_REQUEST",
                            "Every selected metered service requires one quota rule");
            }
            check(!content.getBillingOptions().isEmpty(), 400, "INVALID_REQUEST", "At least one paid billing option is required");
            Set<String> periods = new HashSet<>();
            for (PlanBillingOptionVo option : content.getBillingOptions()) {
                check(periods.add(option.getBillingPeriod().name()), 400, "INVALID_REQUEST", "Billing periods must be unique");
                check(option.getAmount() > 0 && "VND".equals(option.getCurrency()), 400, "INVALID_REQUEST",
                        "Paid options require a positive VND price");
                try { Currency.getInstance(option.getCurrency()); }
                catch (IllegalArgumentException invalid) { throw error(400, "INVALID_REQUEST", "Currency must be ISO4217"); }
            }
        }
    }

    private void validatePublishable(ProductContentVo content) {
        validateSupported(content, true);
    }

    private List<String> changedQuotaTypes(SubscriptionProductVersion current, ProductContentVo next) {
        Map<String, QuotaRuleVo> old = new HashMap<>();
        if (current != null) for (QuotaRuleVo rule : current.getContent().getQuotaRules()) old.put(rule.getQuotaType(), rule);
        Map<String, QuotaRuleVo> fresh = new HashMap<>();
        for (QuotaRuleVo rule : next.getQuotaRules()) fresh.put(rule.getQuotaType(), rule);
        return java.util.stream.Stream.concat(old.keySet().stream(), fresh.keySet().stream()).distinct()
                .filter(key -> !Objects.equals(old.get(key), fresh.get(key))).sorted().toList();
    }

    private String uuidKey(String key) {
        try {
            String normalized = UUID.fromString(key).toString();
            if (!normalized.equalsIgnoreCase(key)) throw new IllegalArgumentException();
            return normalized.toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) {
            throw error(400, "INVALID_REQUEST", "A UUID Idempotency-Key is required");
        }
    }

    private String fingerprint(String planCode, long revision, Boolean replaceScheduled, Long scheduleVersion,
            Instant scheduleEffectiveAt) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(Arrays.asList(planCode, revision,
                            Boolean.TRUE.equals(replaceScheduled), scheduleVersion, scheduleEffectiveAt))));
        } catch (Exception failure) {
            throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product configuration cannot be confirmed");
        }
    }

    private SubscriptionProductPublicationReceipt publicationReceipt(String actor, String key) {
        try {
            return products.findPublicationReceipt(actor, key);
        } catch (RuntimeException failure) {
            throw error(503, "PLAN_CONFIGURATION_UNAVAILABLE", "Product publication cannot be confirmed");
        }
    }

    private boolean unknownCommit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof MongoException mongo && mongo.hasErrorLabel("UnknownTransactionCommitResult")) return true;
        return false;
    }

    private boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException || cause instanceof OptimisticLockingFailureException) return true;
            if (cause instanceof MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError")
                    || mongo.getCode() == 112 || mongo.getCode() == 11000)) return true;
        }
        return false;
    }

    private boolean validCode(String planCode) {
        return planCode != null && planCode.matches("[A-Z][A-Z0-9_-]{0,63}");
    }
}
