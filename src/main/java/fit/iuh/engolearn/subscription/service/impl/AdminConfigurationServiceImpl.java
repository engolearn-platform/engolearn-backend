package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;

import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminConfigurationServiceImpl implements AdminConfigurationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SubscriptionRepository store;
    private final SubscriptionTransactions transactions;
    private final Clock clock;
    private final ObjectMapper mapper;

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    private SubscriptionRequestException error(int status, String code, String detail) {
        return new SubscriptionRequestException(status, code, detail);
    }

    private void check(boolean valid, String code, String detail) {
        if (!valid) throw error(409, code, detail);
    }

    private SubscriptionRequestException unavailable() {
        return error(503, "CONFIGURATION_UNAVAILABLE", "Published configuration cannot be confirmed");
    }

    private <T> T read(Supplier<T> action) {
        try {
            store.ensurePublicationStorage();
            return transactions.execute(action);
        } catch (SubscriptionRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private List<PolicyVersion> policies(String scope) {
        return store.findPolicyVersions()
                    .stream()
                    .filter(p -> p.getScope().name().equals(scope))
                    .sorted(Comparator.comparingLong(PolicyVersion::getVersion).reversed())
                    .toList();
    }

    private PolicyVersion eligible(String scope, Instant at) {
        return policies(scope).stream().filter(p -> !p.getEffectiveAt().isAfter(at)).findFirst().orElse(null);
    }

    private Map<String, QuotaTypeResponse> canonical() {
        Map<String, QuotaTypeResponse> result = new TreeMap<>();
        for (PolicyVersion p : store.findPolicyVersions())
            for (QuotaRuleVo r : p.getSnapshot().getRules()) {
                if (r.getQuotaType() == null || r.getQuotaType().isBlank() || r.getUnit() == null || r.getUnit().isBlank() ||
                    r.getPeriod() == null || !Set.of("DAILY", "WEEKLY", "MONTHLY").contains(r.getPeriod().name()))
                    throw unavailable();
                QuotaTypeResponse type = new QuotaTypeResponse(r.getQuotaType(), r.getUnit(), r.getPeriod().name());
                QuotaTypeResponse prior = result.putIfAbsent(r.getQuotaType(), type);
                if (prior != null && !prior.equals(type)) throw unavailable();
            }
        if (result.isEmpty()) throw unavailable();
        return result;
    }

    private void baseline(Instant at) {
        for (String scope : List.of("FREE", "TRIAL")) {
            PolicyVersion p = eligible(scope, at);
            PolicyState state = store.findPolicyState(scope);
            if (p == null || state == null) throw unavailable();
            String selected = state.getPendingVersionId() != null && !at.isBefore(
                    state.getPendingEffectiveAt()) ? state.getPendingVersionId() : state.getActiveVersionId();
            if (!p.getId().equals(selected)) throw unavailable();
            content(p);
        }
    }

    public ConfigurationCatalogResponse catalog() {
        return read(() -> {
            Instant at = now();
            baseline(at);
            return new ConfigurationCatalogResponse(store.readConfigurationRevision(), at, ZONE.getId(), List.copyOf(canonical().values()),
                               List.of("FREE", "TRIAL", "PREMIUM"));
        });
    }

    private ConfigurationVersionResponse version(PolicyVersion p, Instant at) {
        PolicyVersion current = eligible(p.getScope().name(), at);
        return new ConfigurationVersionResponse(p.getScope().name(), p.getVersion(), p.getEffectiveAt(),
                           p.getEffectiveAt().isAfter(at) ? "SCHEDULED" :
                                   current != null && current.getId().equals(p.getId()) ? "CURRENT" : "HISTORICAL",
                           content(p));
    }

    private ConfigurationContentRequest content(PolicyVersion p) {
        CurrentBenefitsVo benefits = p.getBenefits();
        if (benefits == null || !p.getId().equals(benefits.getPolicySnapshotId()) ||
                !(p.getScope().name().equals("FREE") ? "FREE" : "PREMIUM").equals(benefits.getPlanCode().name()))
            throw unavailable();
        PlanVersion offer = null;
        if (!p.getScope().name().equals("TRIAL")) {
            List<PlanVersion> bound = store.findPlanVersions().stream()
                    .filter(v -> v.getScope() == p.getScope() && v.getVersion() == p.getVersion() &&
                            v.getEffectiveAt().equals(p.getEffectiveAt()) && v.isPublished()).toList();
            if (bound.size() != 1) throw unavailable();
            offer = bound.getFirst();
            PlanCardVo card = offer.getPlan();
            if (!card.getPlanCode().equals(benefits.getPlanCode()) ||
                    !card.getDisplayName().equals(benefits.getDisplayName()) ||
                    !card.getBenefits().equals(benefits.getBenefits())) throw unavailable();
        }
        return SubscriptionModelMapper.adminContent(p, offer);
    }

    public AdminConfigurationResponse configuration(String scope) {
        return read(() -> {
            Instant at = now();
            baseline(at);
            canonical();
            PolicyState state = store.findPolicyState(scope);
            PolicyVersion current = eligible(scope, at), pending =
                    state == null || state.getPendingVersionId() == null ? null : store.findPolicyVersion(
                            state.getPendingVersionId());
            if (state != null && state.getPendingVersionId() != null && pending == null) throw unavailable();
            return new AdminConfigurationResponse(store.readConfigurationRevision(), at, scope,
                                          current == null ? null : version(current, at),
                                          pending != null && pending.getEffectiveAt().isAfter(at) ? version(pending,
                                                                                                            at) : null,
                                          pending != null && !pending.getEffectiveAt().isAfter(at));
        });
    }

    public ConfigurationHistoryResponse history(String scope, int limit, Long before) {
        return read(() -> {
            Instant at = now();
            baseline(at);
            List<PolicyVersion> all =
                    policies(scope).stream().filter(p -> before == null || p.getVersion() < before).toList();
            List<ConfigurationVersionResponse> items = all.stream().limit(limit).map(p -> version(p, at)).toList();
            return new ConfigurationHistoryResponse(items, all.size() > limit ? items.getLast().version() : null);
        });
    }

    public ConfigurationVersionResponse detail(String scope, long number) {
        return read(() -> {
            Instant at = now();
            baseline(at);
            return version(policies(scope).stream()
                                          .filter(p -> p.getVersion() == number)
                                          .findFirst()
                                          .orElseThrow(() -> error(404, "CONFIGURATION_VERSION_NOT_FOUND",
                                                                   "Configuration version was not found")), at);
        });
    }

    public AdminPublicationRequest normalize(AdminPublicationRequest input) {
        ConfigurationContentRequest c = input.content();
        ConfigurationContentRequest normalized =
                new ConfigurationContentRequest(c.displayName().trim(), c.description() == null ? null : c.description().trim(),
                            c.displayOrder(),
                            c.benefits()
                             .stream()
                             .map(b -> new PlanBenefitRequest(b.code().trim(), b.title().trim(), b.description().trim(),
                                                   b.displayValue(), b.sortOrder()))
                             .sorted(Comparator.comparing(PlanBenefitRequest::code))
                             .toList(),
                            c.billingOptions()
                             .stream()
                             .sorted(Comparator.comparing(PlanBillingOptionRequest::billingPeriod))
                             .toList(),
                            c.quotaRules()
                             .stream()
                             .map(r -> new QuotaRuleRequest(r.quotaType().trim(), r.displayName().trim(), r.unit().trim(),
                                                r.period(), r.limit(), r.displayOrder()))
                             .sorted(Comparator.comparing(QuotaRuleRequest::quotaType))
                             .toList());
        return new AdminPublicationRequest(input.expectedRevision(), new PublicationEffectRequest(input.effect().mode(),
                                                                              input.effect().at() ==
                                                                              null ? null : Instant.ofEpochMilli(
                                                                                      input.effect()
                                                                                           .at()
                                                                                           .toEpochMilli())),
                                         normalized);
    }

    private ConfigurationPreviewResponse previewAt(String scope, AdminPublicationRequest input, Instant at) {
        baseline(at);
        long revision = store.readConfigurationRevision();
        check(revision == input.expectedRevision(), "REVISION_CONFLICT", "Configuration changed; reload before saving");
        PolicyState state = store.findPolicyState(scope);
        check(state == null || state.getPendingVersionId() == null, "PENDING_PUBLICATION_EXISTS",
              "A publication is awaiting activation");
        ConfigurationContentRequest c = input.content();
        PublicationEffectRequest effect = input.effect();
        if (scope.equals("FREE") ? (!effect.mode().equals("NEXT_LOCAL_MIDNIGHT") || effect.at() != null) : (
                !Set.of("NOW", "AT").contains(effect.mode()) || effect.mode().equals("NOW") && effect.at() != null ||
                effect.mode().equals("AT") && effect.at() == null))
            throw error(400, "INVALID_REQUEST", "Invalid effective-time intent");
        if (!scope.equals("PREMIUM") && !c.billingOptions().isEmpty() ||
            scope.equals("TRIAL") && (c.description() != null || c.displayOrder() != 0))
            throw error(400, "INVALID_REQUEST", "Invalid scope metadata or billing options");
        Set<String> codes = new HashSet<>(), periods = new HashSet<>(), types = new HashSet<>();
        for (PlanBenefitRequest b : c.benefits())
            if (!codes.add(b.code())) throw error(400, "INVALID_REQUEST", "Benefit codes must be unique");
        for (PlanBillingOptionRequest b : c.billingOptions()) {
            try {
                Currency.getInstance(b.currency());
            } catch (IllegalArgumentException ex) {
                throw error(400, "INVALID_REQUEST", "Currency must be ISO4217");
            }
            if (!periods.add(b.billingPeriod())) throw error(400, "INVALID_REQUEST", "Billing periods must be unique");
        }
        Map<String, QuotaTypeResponse> known = canonical();
        for (QuotaRuleRequest r : c.quotaRules()) {
            if (!types.add(r.quotaType())) throw error(400, "INVALID_REQUEST", "Quota types must be unique");
            QuotaTypeResponse prior = known.get(r.quotaType());
            check(prior == null || prior.unit().equals(r.unit()) && prior.period().equals(r.period()),
                  "CANONICAL_QUOTA_CONFLICT", "Known quota unit and period cannot change");
        }
        PolicyVersion current = eligible(scope, at);
        Map<String, QuotaRuleVo> old = new HashMap<>();
        if (current != null) {
            content(current);
            for (QuotaRuleVo r : current.getSnapshot().getRules()) old.put(r.getQuotaType(), r);
        }
        check(types.containsAll(old.keySet()), "CONFIGURATION_CONFLICT", "Existing quota types cannot be removed");
        Instant effective = scope.equals("FREE") ? at.atZone(ZONE)
                                                     .toLocalDate()
                                                     .plusDays(1)
                                                     .atStartOfDay(ZONE)
                                                     .toInstant() : effect.mode().equals("NOW") ? at : effect.at();
        check(!effect.mode().equals("AT") || effective.isAfter(at), "INVALID_EFFECTIVE_TIME",
              "Scheduled time must be in the future");
        List<String> changed = c.quotaRules()
                                .stream()
                                .filter(r -> !old.containsKey(r.quotaType()) ||
                                             old.get(r.quotaType()).getLimit() != r.limit() ||
                                             !old.get(r.quotaType()).getUnit().equals(r.unit()) ||
                                             !old.get(r.quotaType()).getPeriod().name().equals(r.period()))
                                .map(QuotaRuleRequest::quotaType)
                                .sorted()
                                .toList();
        return new ConfigurationPreviewResponse(revision, scope, effective, changed,
                           scope.equals("FREE") ? "FREE_CHANGED_TYPES_AT_EFFECTIVE_TIME" : "NEW_PERIODS_ONLY", c);
    }

    public ConfigurationPreviewResponse preview(String scope, AdminPublicationRequest input) {
        AdminPublicationRequest normalized = normalize(input);
        return read(() -> previewAt(scope, normalized, now()));
    }

    private String fingerprint(String scope, AdminPublicationRequest input) {
        try {
            return HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                                    .digest(mapper.writeValueAsBytes(List.of(scope, input))));
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private Accepted replay(AdminPublicationReceipt receipt, String hash) {
        check(receipt.getFingerprint().equals(hash), "IDEMPOTENCY_CONFLICT",
              "The intent key was already used for different content");
        return new Accepted(SubscriptionModelMapper.toDto(receipt.getResult()), false);
    }

    public Accepted publish(String actor, String key, String scope, AdminPublicationRequest input) {
        AdminPublicationRequest normalized = normalize(input);
        String hash = fingerprint(scope, normalized);
        try {
            store.ensurePublicationStorage();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(() -> {
                    AdminPublicationReceipt receipt = store.findAdminPublicationReceipt(actor, key);
                    if (receipt != null) return replay(receipt, hash);
                    Instant accepted = now();
                    ConfigurationPreviewResponse preview = previewAt(scope, normalized, accepted);
                    check(store.advanceConfigurationRevision(preview.revision()), "REVISION_CONFLICT",
                          "Configuration changed; reload before saving");
                    long number = policies(scope).stream().mapToLong(PolicyVersion::getVersion).max().orElse(0) + 1;
                    check(number <= 9_007_199_254_740_991L, "CONFIGURATION_CONFLICT", "Version range exhausted");
                    String id = UUID.randomUUID().toString();
                    ConfigurationContentRequest c = normalized.content();
                    String planCode = scope.equals("FREE") ? "FREE" : "PREMIUM";
                    PolicyVersion policy = SubscriptionModelMapper.policyVersion(id, scope, number,
                            preview.effectiveAt(), planCode, c);
                    store.publishPolicyVersion(policy);
                    if (!scope.equals("TRIAL")) store.publishPlanVersion(SubscriptionModelMapper.planVersion(
                            UUID.randomUUID().toString(), scope, number, preview.effectiveAt(), planCode, c));
                    PolicyState state = store.findPolicyState(scope);
                    if (state == null) store.initializePolicyState(
                            normalized.effect().mode().equals("AT") ? SubscriptionModelMapper.policyState(scope, null,
                                                                                                          id,
                                                                                                          preview.effectiveAt(),
                                                                                                          0, 0,
                                                                                                          hash) : SubscriptionModelMapper.policyState(
                                    scope, id, null, null, 1, 0, hash));
                    else check(normalized.effect().mode().equals("NOW") ? store.replaceActivePolicy(state,
                                                                                                    policy) : store.schedulePolicy(
                            state, policy), "PENDING_PUBLICATION_EXISTS", "A publication is awaiting activation");
                    PublicationResultResponse result = new PublicationResultResponse(id, scope, number, preview.effectiveAt(), accepted,
                                                                     preview.revision() + 1);
                    store.recordAdminPublication(
                            SubscriptionModelMapper.adminPublicationReceipt(UUID.randomUUID().toString(), actor,
                                                                            "PUBLISH_CONFIGURATION", key, hash,
                                                                            accepted, result));
                    return new Accepted(result, true);
                });
            } catch (SubscriptionRequestException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                boolean unknown = hasLabel(ex, "UnknownTransactionCommitResult");
                try {
                    AdminPublicationReceipt receipt = store.findAdminPublicationReceipt(actor, key);
                    if (receipt != null) return replay(receipt, hash);
                } catch (SubscriptionRequestException conflict) {
                    throw conflict;
                } catch (RuntimeException lookup) {
                    throw unavailable();
                }
                if (!unknown && retryable(ex) && attempt < 2) continue;
                throw unavailable();
            }
        }
        throw unavailable();
    }

    private boolean hasLabel(Throwable ex, String label) {
        for (Throwable c = ex; c != null; c = c.getCause())
            if (c instanceof com.mongodb.MongoException m && m.hasErrorLabel(label)) return true;
        return false;
    }

    private boolean retryable(Throwable ex) {
        for (Throwable c = ex; c != null; c = c.getCause())
            if (c instanceof DuplicateKeyException ||
                c instanceof org.springframework.dao.OptimisticLockingFailureException ||
                c instanceof com.mongodb.MongoException m &&
                (m.hasErrorLabel("TransientTransactionError") || m.getCode() == 112 || m.getCode() == 11000))
                return true;
        return false;
    }
}
