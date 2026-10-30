package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.vo.QuotaRuleVo;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class SubscriptionPublicationServiceImpl implements SubscriptionPublicationService {
    private static final long MAX=9_007_199_254_740_991L;
    private final SubscriptionRepository store;
    private final SubscriptionTransactions transactions;
    private final Clock clock; private final ObjectMapper mapper;
    public void publish(PublicationManifestRequest manifest) {
        validate(manifest); store.ensurePublicationStorage();
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(manifest))); }
        catch(Exception ex) { throw new IllegalArgumentException("Manifest cannot be canonicalized",ex); }
        transactions.execute(()->{
            PublicationReceipt receipt=store.findManifestPublicationReceipt(manifest.manifestId());
            if(receipt!=null) { require(receipt.getFingerprint().equals(fingerprint),"Manifest ID reused with changed content"); return null; }
            require(store.advanceConfigurationRevision(store.readConfigurationRevision()),"Configuration revision changed");
            Map<String,String> canonical=new HashMap<>();
            for(PolicyVersion existing:store.findPolicyVersions()) for(QuotaRuleVo r:existing.getSnapshot().getRules()) {String prior=canonical.putIfAbsent(r.getQuotaType(),r.getUnit()+":"+r.getPeriod().name());require(prior==null||prior.equals(r.getUnit()+":"+r.getPeriod().name()),"Canonical unit/period cannot change");}
            for(PublicationManifestRequest.PolicyRequest p:manifest.policies()) {
                for(QuotaRuleDto r:p.snapshot().rules()) { String previous=canonical.putIfAbsent(r.quotaType(),r.unit()+":"+r.period()); require(previous==null || previous.equals(r.unit()+":"+r.period()),"Canonical unit/period cannot change"); }
                PolicyState state=store.findPolicyState(p.scope());
                if(state==null) { require(!p.effectiveAt().isAfter(clock.instant()),"Initial policy must already be effective"); store.publishPolicyVersion(SubscriptionModelMapper.policyVersion(p.id(),p.scope(),p.version(),p.effectiveAt(),p.snapshot(),p.benefits()));store.initializePolicyState(SubscriptionModelMapper.policyState(p.scope(),p.id(),null,null,1,0,fingerprint)); }
                else {
                    PolicyVersion old=store.findPolicyVersion(state.getActiveVersionId()); require(p.version()>old.getVersion(),"Policy version must increase");
                    Instant expected=clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                    require(!"FREE".equals(p.scope()) || expected.equals(p.effectiveAt()),"Free policy activates at next local midnight");
                    require(p.effectiveAt().isAfter(clock.instant()),"Updates require future effectiveAt");
                    store.publishPolicyVersion(SubscriptionModelMapper.policyVersion(p.id(),p.scope(),p.version(),p.effectiveAt(),p.snapshot(),p.benefits()));require(store.schedulePolicy(state,SubscriptionModelMapper.policyVersion(p.id(),p.scope(),p.version(),p.effectiveAt(),p.snapshot(),p.benefits())),"Policy update is already pending or revision changed");
                }
            }
            require(store.findPolicyState("FREE")!=null && store.findPolicyState("TRIAL")!=null,"FREE and TRIAL policies are mandatory");
            for(PublicationManifestRequest.PlanRequest p:manifest.plans()) store.publishPlanVersion(SubscriptionModelMapper.planVersion(p.id(),p.scope(),p.version(),p.effectiveAt(),p.published(),p.displayOrder(),p.plan()));
            for(var a:manifest.accounts()) {
                require(store.findAccount(a.userId())==null,"History replacement is forbidden");
                store.initializeAccountHistory(SubscriptionModelMapper.subscriptionAccount(a.userId(),a.trialUsed(),0,0,a.provenance()));
            }
            store.recordManifestPublication(SubscriptionModelMapper.publicationReceipt(manifest.manifestId(),fingerprint,manifest.environment()));return null;
        });
    }
    private void validate(PublicationManifestRequest m) {
        require(m!=null,"Manifest required"); text(m.manifestId());text(m.environment());
        require(m.plans()!=null && m.policies()!=null && m.accounts()!=null,"All manifest arrays required");
        Set<String> scopes=new HashSet<>();Set<String> ids=new HashSet<>();
        for(PublicationManifestRequest.PolicyRequest p:m.policies()) {
            text(p.id());require(ids.add(p.id()) && scopes.add(p.scope()),"Duplicate policy ID/scope");
            require(Set.of("FREE","TRIAL","PREMIUM").contains(p.scope()),"Unknown scope");
            require(p.version()>0 && p.version()<=MAX && p.effectiveAt()!=null,"Invalid policy version/date");
            QuotaPolicySnapshotDto s=p.snapshot();require(s!=null && p.id().equals(s.policySnapshotId()) && p.scope().equals(s.policyScope()) && p.version()==s.policyVersion(),"Snapshot metadata mismatch");
            require(s.rules()!=null && !s.rules().isEmpty(),"Quota rules required");
            Set<String> types=new HashSet<>();
            for(QuotaRuleDto r:s.rules()) { text(r.quotaType());text(r.displayName());require(types.add(r.quotaType()),"Duplicate quota type");require(r.limit()>=0 && r.limit()<=MAX && r.displayOrder()>=0,"Invalid quota numeric value");text(r.unit());require(Set.of("DAILY","WEEKLY","MONTHLY").contains(r.period()),"Invalid calendar period"); }
            CurrentBenefitsResponse b=p.benefits();require(b!=null && p.id().equals(b.policySnapshotId()),"Benefits snapshot required");text(b.displayName());require(("FREE".equals(p.scope())?"FREE":"PREMIUM").equals(b.planCode()),"Benefits plan mismatch");benefits(b.benefits());
        }
        Set<String> planIds=new HashSet<>();
        for(PublicationManifestRequest.PlanRequest p:m.plans()) {
            text(p.id());require(planIds.add(p.id()),"Duplicate plan ID");text(p.scope());require(p.version()>0 && p.version()<=MAX && p.effectiveAt()!=null && p.displayOrder()>=0,"Invalid offer metadata");
            PlanCardResponse c=p.plan();require(c!=null,"Plan required");text(c.planCode());text(c.displayName());benefits(c.benefits());
            require(c.billingOptions()!=null,"Billing options required");require(!"FREE".equals(c.planCode()) || c.billingOptions().isEmpty(),"Free cannot have a price");
            Set<String> periods=new HashSet<>();for(PlanBillingOptionResponse b:c.billingOptions()) { require(Set.of("MONTHLY","ANNUAL").contains(b.billingPeriod()) && periods.add(b.billingPeriod()),"Invalid/duplicate billing period");require(b.amount()>=0 && b.amount()<=MAX,"Invalid amount");require(b.currency()!=null && b.currency().matches("[A-Z]{3}"),"Invalid currency");Currency.getInstance(b.currency()); }
        }
        Set<String> users=new HashSet<>(); for(var a:m.accounts()) { text(a.userId()); require(users.add(a.userId()),"Duplicate account");require(Set.of("NEW_ACCOUNT","MIGRATED").contains(a.provenance()),"Explicit account history provenance required"); }
    }
    private void benefits(List<PlanBenefitResponse> benefits) { require(benefits!=null,"Benefits required");Set<String> codes=new HashSet<>();for(PlanBenefitResponse b:benefits) {text(b.code());text(b.title());text(b.description());require(codes.add(b.code()) && b.sortOrder()>=0,"Invalid benefit");} }
    private void text(String value) { require(value!=null && !value.isBlank(),"Nonblank identifiers/text required"); }
    private void require(boolean condition,String reason) { if(!condition) throw new IllegalArgumentException(reason); }
}
