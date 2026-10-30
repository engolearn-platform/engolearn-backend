package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationInput;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationInput.*;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.dto.response.AdminConfiguration.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionStore;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminConfigurationService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final SubscriptionStore store;
    private final SubscriptionTransactions transactions;
    private final SubscriptionIndexes indexes;
    private final Clock clock;
    private final ObjectMapper mapper;
    public AdminConfigurationService(SubscriptionStore store,SubscriptionTransactions transactions,SubscriptionIndexes indexes,Clock clock,ObjectMapper mapper) {
        this.store=store;this.transactions=transactions;this.indexes=indexes;this.clock=clock;this.mapper=mapper;
    }
    public record Accepted(PublicationResult result,boolean created) {}
    private Instant now(){return Instant.ofEpochMilli(clock.millis());}
    private SubscriptionRequestException error(int status,String code,String detail){return new SubscriptionRequestException(status,code,detail);}
    private void check(boolean valid,String code,String detail){if(!valid)throw error(409,code,detail);}
    private SubscriptionRequestException unavailable(){return error(503,"CONFIGURATION_UNAVAILABLE","Published configuration cannot be confirmed");}
    private <T> T read(Supplier<T> action) {
        try {indexes.ensure();return transactions.execute(action);}
        catch(SubscriptionRequestException ex){throw ex;}catch(RuntimeException ex){throw unavailable();}
    }
    private List<PolicyVersion> policies(String scope){return store.policies().stream().filter(p->p.scope().equals(scope)).sorted(Comparator.comparingLong(PolicyVersion::version).reversed()).toList();}
    private PolicyVersion eligible(String scope,Instant at){return policies(scope).stream().filter(p->!p.effectiveAt().isAfter(at)).findFirst().orElse(null);}
    private Map<String,QuotaType> canonical() {
        Map<String,QuotaType> result=new TreeMap<>();
        for(PolicyVersion p:store.policies())for(QuotaRule r:p.snapshot().rules()) {
            if(r.quotaType()==null||r.quotaType().isBlank()||r.unit()==null||r.unit().isBlank()||r.period()==null||!Set.of("DAILY","WEEKLY","MONTHLY").contains(r.period()))throw unavailable();
            QuotaType type=new QuotaType(r.quotaType(),r.unit(),r.period());
            QuotaType prior=result.putIfAbsent(r.quotaType(),type);
            if(prior!=null&&!prior.equals(type))throw unavailable();
        }
        if(result.isEmpty())throw unavailable();
        return result;
    }
    private void baseline(Instant at) {
        for(String scope:List.of("FREE","TRIAL")) {
            PolicyVersion p=eligible(scope,at);
            PolicyState state=store.state(scope);
            if(p==null||state==null)throw unavailable();
            String selected=state.pendingVersionId()!=null&&!at.isBefore(state.pendingEffectiveAt())?state.pendingVersionId():state.activeVersionId();
            if(!p.id().equals(selected))throw unavailable();
            content(p);
        }
    }
    public Catalog catalog(){return read(()->{Instant at=now();baseline(at);return new Catalog(store.configurationRevision(),at,ZONE.getId(),List.copyOf(canonical().values()),List.of("FREE","TRIAL","PREMIUM"));});}
    private Version version(PolicyVersion p,Instant at) {
        PolicyVersion current=eligible(p.scope(),at);
        return new Version(p.scope(),p.version(),p.effectiveAt(),p.effectiveAt().isAfter(at)?"SCHEDULED":current!=null&&current.id().equals(p.id())?"CURRENT":"HISTORICAL",content(p));
    }
    private Content content(PolicyVersion p) {
        CurrentBenefits benefits=p.benefits();
        if(benefits==null||!p.id().equals(benefits.policySnapshotId())||!(p.scope().equals("FREE")?"FREE":"PREMIUM").equals(benefits.planCode()))throw unavailable();
        String description=null;int order=0;List<BillingOption> billing=List.of();
        if(!p.scope().equals("TRIAL")) {
            List<PlanVersion> bound=store.allPlans().stream().filter(v->v.scope().equals(p.scope())&&v.version()==p.version()&&v.effectiveAt().equals(p.effectiveAt())&&v.published()).toList();
            if(bound.size()!=1)throw unavailable();
            PlanVersion offer=bound.getFirst();PlanCard card=offer.plan();
            if(!card.planCode().equals(benefits.planCode())||!card.displayName().equals(benefits.displayName())||!card.benefits().equals(benefits.benefits()))throw unavailable();
            description=card.description();order=offer.displayOrder();
            billing=card.billingOptions().stream().map(b->new BillingOption(b.billingPeriod(),b.amount(),b.currency())).toList();
        }
        return new Content(benefits.displayName(),description,order,benefits.benefits().stream().map(b->new Benefit(b.code(),b.title(),b.description(),b.displayValue(),b.sortOrder())).toList(),billing,
            p.snapshot().rules().stream().map(r->new Rule(r.quotaType(),r.displayName(),r.unit(),r.period(),r.limit(),r.displayOrder())).toList());
    }
    public AdminConfiguration configuration(String scope){return read(()->{
        Instant at=now();baseline(at);canonical();PolicyState state=store.state(scope);
        PolicyVersion current=eligible(scope,at),pending=state==null||state.pendingVersionId()==null?null:store.policy(state.pendingVersionId());
        if(state!=null&&state.pendingVersionId()!=null&&pending==null)throw unavailable();
        return new AdminConfiguration(store.configurationRevision(),at,scope,current==null?null:version(current,at),pending!=null&&pending.effectiveAt().isAfter(at)?version(pending,at):null,pending!=null&&!pending.effectiveAt().isAfter(at));
    });}
    public History history(String scope,int limit,Long before){return read(()->{
        Instant at=now();baseline(at);List<PolicyVersion> all=policies(scope).stream().filter(p->before==null||p.version()<before).toList();
        List<Version> items=all.stream().limit(limit).map(p->version(p,at)).toList();
        return new History(items,all.size()>limit?items.getLast().version():null);
    });}
    public Version detail(String scope,long number){return read(()->{Instant at=now();baseline(at);return version(policies(scope).stream().filter(p->p.version()==number).findFirst().orElseThrow(()->error(404,"CONFIGURATION_VERSION_NOT_FOUND","Configuration version was not found")),at);});}
    public AdminPublicationInput normalize(AdminPublicationInput input) {
        Content c=input.content();
        Content normalized=new Content(c.displayName().trim(),c.description()==null?null:c.description().trim(),c.displayOrder(),
            c.benefits().stream().map(b->new Benefit(b.code().trim(),b.title().trim(),b.description().trim(),b.displayValue(),b.sortOrder())).sorted(Comparator.comparing(Benefit::code)).toList(),
            c.billingOptions().stream().sorted(Comparator.comparing(BillingOption::billingPeriod)).toList(),
            c.quotaRules().stream().map(r->new Rule(r.quotaType().trim(),r.displayName().trim(),r.unit().trim(),r.period(),r.limit(),r.displayOrder())).sorted(Comparator.comparing(Rule::quotaType)).toList());
        return new AdminPublicationInput(input.expectedRevision(),new Effect(input.effect().mode(),input.effect().at()==null?null:Instant.ofEpochMilli(input.effect().at().toEpochMilli())),normalized);
    }
    private Preview previewAt(String scope,AdminPublicationInput input,Instant at) {
        baseline(at);long revision=store.configurationRevision();
        check(revision==input.expectedRevision(),"REVISION_CONFLICT","Configuration changed; reload before saving");
        PolicyState state=store.state(scope);
        check(state==null||state.pendingVersionId()==null,"PENDING_PUBLICATION_EXISTS","A publication is awaiting activation");
        Content c=input.content();Effect effect=input.effect();
        if(scope.equals("FREE")?(!effect.mode().equals("NEXT_LOCAL_MIDNIGHT")||effect.at()!=null):(!Set.of("NOW","AT").contains(effect.mode())||effect.mode().equals("NOW")&&effect.at()!=null||effect.mode().equals("AT")&&effect.at()==null))throw error(400,"INVALID_REQUEST","Invalid effective-time intent");
        if(!scope.equals("PREMIUM")&&!c.billingOptions().isEmpty()||scope.equals("TRIAL")&&(c.description()!=null||c.displayOrder()!=0))throw error(400,"INVALID_REQUEST","Invalid scope metadata or billing options");
        Set<String> codes=new HashSet<>(),periods=new HashSet<>(),types=new HashSet<>();
        for(Benefit b:c.benefits())if(!codes.add(b.code()))throw error(400,"INVALID_REQUEST","Benefit codes must be unique");
        for(BillingOption b:c.billingOptions()) {
            try {Currency.getInstance(b.currency());}catch(IllegalArgumentException ex){throw error(400,"INVALID_REQUEST","Currency must be ISO4217");}
            if(!periods.add(b.billingPeriod()))throw error(400,"INVALID_REQUEST","Billing periods must be unique");
        }
        Map<String,QuotaType> known=canonical();
        for(Rule r:c.quotaRules()) {
            if(!types.add(r.quotaType()))throw error(400,"INVALID_REQUEST","Quota types must be unique");
            QuotaType prior=known.get(r.quotaType());
            check(prior==null||prior.unit().equals(r.unit())&&prior.period().equals(r.period()),"CANONICAL_QUOTA_CONFLICT","Known quota unit and period cannot change");
        }
        PolicyVersion current=eligible(scope,at);Map<String,QuotaRule> old=new HashMap<>();
        if(current!=null) {content(current);for(QuotaRule r:current.snapshot().rules())old.put(r.quotaType(),r);}
        check(types.containsAll(old.keySet()),"CONFIGURATION_CONFLICT","Existing quota types cannot be removed");
        Instant effective=scope.equals("FREE")?at.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant():effect.mode().equals("NOW")?at:effect.at();
        check(!effect.mode().equals("AT")||effective.isAfter(at),"INVALID_EFFECTIVE_TIME","Scheduled time must be in the future");
        List<String> changed=c.quotaRules().stream().filter(r->!old.containsKey(r.quotaType())||old.get(r.quotaType()).limit()!=r.limit()||!old.get(r.quotaType()).unit().equals(r.unit())||!old.get(r.quotaType()).period().equals(r.period())).map(Rule::quotaType).sorted().toList();
        return new Preview(revision,scope,effective,changed,scope.equals("FREE")?"FREE_CHANGED_TYPES_AT_EFFECTIVE_TIME":"NEW_PERIODS_ONLY",c);
    }
    public Preview preview(String scope,AdminPublicationInput input){AdminPublicationInput normalized=normalize(input);return read(()->previewAt(scope,normalized,now()));}
    private String fingerprint(String scope,AdminPublicationInput input) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(scope,input))));}
        catch(Exception ex){throw unavailable();}
    }
    private Accepted replay(AdminPublicationReceipt receipt,String hash){check(receipt.fingerprint().equals(hash),"IDEMPOTENCY_CONFLICT","The intent key was already used for different content");return new Accepted(receipt.result(),false);}
    public Accepted publish(String actor,String key,String scope,AdminPublicationInput input) {
        AdminPublicationInput normalized=normalize(input);String hash=fingerprint(scope,normalized);
        try {indexes.ensure();}catch(RuntimeException ex){throw unavailable();}
        for(int attempt=0;attempt<3;attempt++) {
            try {return transactions.execute(()->{
                AdminPublicationReceipt receipt=store.adminReceipt(actor,key);if(receipt!=null)return replay(receipt,hash);
                Instant accepted=now();Preview preview=previewAt(scope,normalized,accepted);
                check(store.advanceConfiguration(preview.revision()),"REVISION_CONFLICT","Configuration changed; reload before saving");
                long number=policies(scope).stream().mapToLong(PolicyVersion::version).max().orElse(0)+1;
                check(number<=9_007_199_254_740_991L,"CONFIGURATION_CONFLICT","Version range exhausted");
                String id=UUID.randomUUID().toString();Content c=normalized.content();
                List<PlanBenefit> benefits=c.benefits().stream().map(b->new PlanBenefit(b.code(),b.title(),b.description(),b.displayValue(),b.sortOrder())).toList();
                String planCode=scope.equals("FREE")?"FREE":"PREMIUM";
                PolicyVersion policy=new PolicyVersion(id,scope,number,preview.effectiveAt(),new QuotaPolicySnapshot(id,scope,number,c.quotaRules().stream().map(r->new QuotaRule(r.quotaType(),r.displayName(),r.limit(),r.unit(),r.period(),r.displayOrder())).toList()),new CurrentBenefits(planCode,c.displayName(),id,benefits));
                store.insert(policy);
                if(!scope.equals("TRIAL"))store.insert(new PlanVersion(UUID.randomUUID().toString(),scope,number,preview.effectiveAt(),true,c.displayOrder(),new PlanCard(planCode,c.displayName(),c.description(),benefits,c.billingOptions().stream().map(b->new PlanBillingOption(b.billingPeriod(),b.amount(),b.currency())).toList())));
                PolicyState state=store.state(scope);
                if(state==null)store.insert(normalized.effect().mode().equals("AT")?new PolicyState(scope,null,id,preview.effectiveAt(),0,0,hash):new PolicyState(scope,id,null,null,1,0,hash));
                else check(normalized.effect().mode().equals("NOW")?store.replaceActive(state,policy):store.schedule(state,policy),"PENDING_PUBLICATION_EXISTS","A publication is awaiting activation");
                PublicationResult result=new PublicationResult(id,scope,number,preview.effectiveAt(),accepted,preview.revision()+1);
                store.insert(new AdminPublicationReceipt(UUID.randomUUID().toString(),actor,"PUBLISH_CONFIGURATION",key,hash,accepted,result));
                return new Accepted(result,true);
            });}catch(SubscriptionRequestException ex){throw ex;}catch(RuntimeException ex){
                boolean unknown=hasLabel(ex,"UnknownTransactionCommitResult");
                try {AdminPublicationReceipt receipt=store.adminReceipt(actor,key);if(receipt!=null)return replay(receipt,hash);}catch(SubscriptionRequestException conflict){throw conflict;}catch(RuntimeException lookup){throw unavailable();}
                if(!unknown&&retryable(ex)&&attempt<2)continue;
                throw unavailable();
            }
        }
        throw unavailable();
    }
    private boolean hasLabel(Throwable ex,String label){for(Throwable c=ex;c!=null;c=c.getCause())if(c instanceof com.mongodb.MongoException m&&m.hasErrorLabel(label))return true;return false;}
    private boolean retryable(Throwable ex){for(Throwable c=ex;c!=null;c=c.getCause())if(c instanceof DuplicateKeyException||c instanceof org.springframework.dao.OptimisticLockingFailureException||c instanceof com.mongodb.MongoException m&&(m.hasErrorLabel("TransientTransactionError")||m.getCode()==112||m.getCode()==11000))return true;return false;}
}
