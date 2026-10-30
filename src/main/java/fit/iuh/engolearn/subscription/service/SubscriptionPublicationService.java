package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionStore;
import java.time.*;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;

@Service
public class SubscriptionPublicationService {
    private static final long MAX=9_007_199_254_740_991L;
    private final SubscriptionStore store; private final MongoTemplate mongo;
    private final SubscriptionTransactions transactions; private final SubscriptionIndexes indexes;
    private final Clock clock; private final ObjectMapper mapper;
    public SubscriptionPublicationService(SubscriptionStore store,MongoTemplate mongo,SubscriptionTransactions transactions,SubscriptionIndexes indexes,Clock clock,ObjectMapper mapper) {
        this.store=store;this.mongo=mongo;this.transactions=transactions;this.indexes=indexes;this.clock=clock;this.mapper=mapper;
    }
    public void publish(PublicationManifest manifest) {
        validate(manifest); indexes.ensure();
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(manifest))); }
        catch(Exception ex) { throw new IllegalArgumentException("Manifest cannot be canonicalized",ex); }
        transactions.execute(()->{
            PublicationReceipt receipt=mongo.findById(manifest.manifestId(),PublicationReceipt.class);
            if(receipt!=null) { require(receipt.fingerprint().equals(fingerprint),"Manifest ID reused with changed content"); return null; }
            require(store.advanceConfiguration(store.configurationRevision()),"Configuration revision changed");
            Map<String,String> canonical=new HashMap<>();
            for(PolicyVersion existing:store.policies()) for(QuotaRule r:existing.snapshot().rules()) {String prior=canonical.putIfAbsent(r.quotaType(),r.unit()+":"+r.period());require(prior==null||prior.equals(r.unit()+":"+r.period()),"Canonical unit/period cannot change");}
            for(PolicyVersion p:manifest.policies()) {
                for(QuotaRule r:p.snapshot().rules()) { String previous=canonical.putIfAbsent(r.quotaType(),r.unit()+":"+r.period()); require(previous==null || previous.equals(r.unit()+":"+r.period()),"Canonical unit/period cannot change"); }
                PolicyState state=store.state(p.scope());
                if(state==null) { require(!p.effectiveAt().isAfter(clock.instant()),"Initial policy must already be effective"); store.insert(p);store.insert(new PolicyState(p.scope(),p.id(),null,null,1,0,fingerprint)); }
                else {
                    PolicyVersion old=store.policy(state.activeVersionId()); require(p.version()>old.version(),"Policy version must increase");
                    Instant expected=clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                    require(!"FREE".equals(p.scope()) || expected.equals(p.effectiveAt()),"Free policy activates at next local midnight");
                    require(p.effectiveAt().isAfter(clock.instant()),"Updates require future effectiveAt");
                    store.insert(p);require(store.schedule(state,p),"Policy update is already pending or revision changed");
                }
            }
            require(store.state("FREE")!=null && store.state("TRIAL")!=null,"FREE and TRIAL policies are mandatory");
            for(PlanVersion p:manifest.plans()) store.insert(p);
            for(var a:manifest.accounts()) {
                require(store.account(a.userId())==null,"History replacement is forbidden");
                store.insert(new SubscriptionAccount(a.userId(),a.trialUsed(),0,0,a.provenance()));
            }
            store.insert(new PublicationReceipt(manifest.manifestId(),fingerprint,manifest.environment()));return null;
        });
    }
    private void validate(PublicationManifest m) {
        require(m!=null,"Manifest required"); text(m.manifestId());text(m.environment());
        require(m.plans()!=null && m.policies()!=null && m.accounts()!=null,"All manifest arrays required");
        Set<String> scopes=new HashSet<>();Set<String> ids=new HashSet<>();
        for(PolicyVersion p:m.policies()) {
            text(p.id());require(ids.add(p.id()) && scopes.add(p.scope()),"Duplicate policy ID/scope");
            require(Set.of("FREE","TRIAL","PREMIUM").contains(p.scope()),"Unknown scope");
            require(p.version()>0 && p.version()<=MAX && p.effectiveAt()!=null,"Invalid policy version/date");
            QuotaPolicySnapshot s=p.snapshot();require(s!=null && p.id().equals(s.policySnapshotId()) && p.scope().equals(s.policyScope()) && p.version()==s.policyVersion(),"Snapshot metadata mismatch");
            require(s.rules()!=null && !s.rules().isEmpty(),"Quota rules required");
            Set<String> types=new HashSet<>();
            for(QuotaRule r:s.rules()) { text(r.quotaType());text(r.displayName());require(types.add(r.quotaType()),"Duplicate quota type");require(r.limit()>=0 && r.limit()<=MAX && r.displayOrder()>=0,"Invalid quota numeric value");text(r.unit());require(Set.of("DAILY","WEEKLY","MONTHLY").contains(r.period()),"Invalid calendar period"); }
            CurrentBenefits b=p.benefits();require(b!=null && p.id().equals(b.policySnapshotId()),"Benefits snapshot required");text(b.displayName());require(("FREE".equals(p.scope())?"FREE":"PREMIUM").equals(b.planCode()),"Benefits plan mismatch");benefits(b.benefits());
        }
        Set<String> planIds=new HashSet<>();
        for(PlanVersion p:m.plans()) {
            text(p.id());require(planIds.add(p.id()),"Duplicate plan ID");text(p.scope());require(p.version()>0 && p.version()<=MAX && p.effectiveAt()!=null && p.displayOrder()>=0,"Invalid offer metadata");
            PlanCard c=p.plan();require(c!=null,"Plan required");text(c.planCode());text(c.displayName());benefits(c.benefits());
            require(c.billingOptions()!=null,"Billing options required");require(!"FREE".equals(c.planCode()) || c.billingOptions().isEmpty(),"Free cannot have a price");
            Set<String> periods=new HashSet<>();for(PlanBillingOption b:c.billingOptions()) { require(Set.of("MONTHLY","ANNUAL").contains(b.billingPeriod()) && periods.add(b.billingPeriod()),"Invalid/duplicate billing period");require(b.amount()>=0 && b.amount()<=MAX,"Invalid amount");require(b.currency()!=null && b.currency().matches("[A-Z]{3}"),"Invalid currency");Currency.getInstance(b.currency()); }
        }
        Set<String> users=new HashSet<>(); for(var a:m.accounts()) { text(a.userId()); require(users.add(a.userId()),"Duplicate account");require(Set.of("NEW_ACCOUNT","MIGRATED").contains(a.provenance()),"Explicit account history provenance required"); }
    }
    private void benefits(List<PlanBenefit> benefits) { require(benefits!=null,"Benefits required");Set<String> codes=new HashSet<>();for(PlanBenefit b:benefits) {text(b.code());text(b.title());text(b.description());require(codes.add(b.code()) && b.sortOrder()>=0,"Invalid benefit");} }
    private void text(String value) { require(value!=null && !value.isBlank(),"Nonblank identifiers/text required"); }
    private void require(boolean condition,String reason) { if(!condition) throw new IllegalArgumentException(reason); }
}
