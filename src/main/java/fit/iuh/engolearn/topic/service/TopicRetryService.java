package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.*;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class TopicRetryService {
    @org.springframework.beans.factory.annotation.Autowired
    private TopicGenerationService generations;
    private final LocalLearnerProvider learner;
    private final TopicLearnerCatalogService catalog;
    private final TopicItemRepository items;
    private final TopicItemVersionRepository versions;
    private final TopicRepository topics;
    private final TopicVersionRepository releases;
    private final MongoTemplate mongo;
    private final TopicQuizMapper quiz;
    private final TopicAttemptPresenter presenter;

    public TopicRetryService(LocalLearnerProvider learner, TopicLearnerCatalogService catalog, TopicItemRepository items,
            TopicItemVersionRepository versions, TopicRepository topics, TopicVersionRepository releases,
            MongoTemplate mongo, TopicQuizMapper quiz, TopicAttemptPresenter presenter) {
        this.learner=learner;this.catalog=catalog;this.items=items;this.versions=versions;this.topics=topics;
        this.releases=releases;this.mongo=mongo;this.quiz=quiz;this.presenter=presenter;
    }

    public boolean isRetry(String id) { return mongo.exists(new Query(Criteria.where("_id").is(id).and("kind").is("ATTEMPT")),TopicRetryLocator.class); }

    public UserTopicItemAttempt owned(String id, boolean result) {
        TopicRetryLocator locator=locator(id,"ATTEMPT");
        TopicRetryRuntime runtime=runtime(locator.getTopicId(),locator.getGeneration());
        UserTopicItemAttempt found=find(runtime,id);
        if(found==null) throw problem(409,result?"RESULT_CHANGED":"RETRY_SESSION_CHANGED","Open the current result or retry from the topic");
        if(found.getStatus()==AttemptStatus.IN_PROGRESS && !Objects.equals(runtime.getSessionId(),locator.getSessionId()))
            throw problem(409,"RETRY_SESSION_CHANGED","The retry session changed");
        visible(found);
        return found;
    }

    public TopicAttemptResponse startItem(String itemId,StartTopicAttemptRequest body,String key) {
        String topicId=items.findById(itemId).orElseThrow(()->problem(404,"CONTENT_UNAVAILABLE","Item not found")).getTopicId();
        long generation=generation(topicId);
        if(body.replaceRetrySessionId()!=null) throw problem(409,"RETRY_RESTART_NOT_ALLOWED","An unfinished retry cannot be restarted or replaced");
        if(body.retrySessionId()!=null) {
            var observed=mongo.findById(body.retrySessionId(),TopicRetryLocator.class);
            if(observed!=null && owner().equals(observed.getUserId())) requireGeneration(owner(),observed.getTopicId(),observed.getGeneration());
        }
        String fingerprint="item|"+itemId+"|"+body.retrySessionId()+"|"+body.replaceRetrySessionId();
        String attemptId=hash(owner()+"|item|"+itemId+"|"+key+scope(generation));
        TopicRetryLocator oldKey=mongo.findOne(new Query(Criteria.where("userId").is(owner()).and("topicId").is(topicId)
                .and("startRequestKey").is(key)),TopicRetryLocator.class);
        if(oldKey!=null) requireGeneration(oldKey.getUserId(),topicId,oldKey.getGeneration());
        TopicRetryLocator legacy=mongo.findById(hash(owner()+"|item|"+itemId+"|"+key),TopicRetryLocator.class);
        if(legacy!=null) requireGeneration(legacy.getUserId(),legacy.getTopicId(),legacy.getGeneration());
        TopicRetryLocator replay=mongo.findById(attemptId,TopicRetryLocator.class);
        if(replay!=null) return replayStart(replay,fingerprint);
        TopicRetryRuntime state=runtime(topicId,generation);
        boolean active=state!=null && "IN_PROGRESS".equals(state.getStatus());
        if(active && state.getCurrentAttempt()!=null) {
            if(body.retrySessionId()!=null && !body.retrySessionId().equals(state.getSessionId())) throw changed();
            if(!itemId.equals(state.getCurrentAttempt().getTopicItemId())) throw problem(409,"ITEM_RETRY_IN_PROGRESS","Finish the active item retry first");
            prepare(TopicRetryLocator.builder().id(attemptId).generation(generation).userId(owner()).topicId(topicId)
                    .sessionId(state.getSessionId()).kind("ATTEMPT").fingerprint(fingerprint).startRequestKey(key)
                    .initialAttempt(state.getCurrentAttempt()).build());
            requireGeneration(owner(),topicId,generation);
            return presenter.describe(state.getCurrentAttempt());
        }
        if(body.retrySessionId()!=null) {
            if(state==null || !body.retrySessionId().equals(state.getSessionId()) || !"IN_PROGRESS".equals(state.getStatus()) || body.replaceRetrySessionId()!=null)
                throw changed();
            if(state.getCurrentAttempt()!=null) {
                if(itemId.equals(state.getCurrentAttempt().getTopicItemId())) return presenter.describe(state.getCurrentAttempt());
                throw changed();
            }
            if(!Objects.equals(next(state),itemId)) throw changed();
            AttemptItemSnapshotVo snapshot=state.getItemsSnapshot().stream().filter(v->v.getTopicItemId().equals(itemId)).findFirst().orElseThrow();
            UserTopicItemAttempt attempt=newAttempt(attemptId,state,itemId,snapshot.getTopicItemVersionId(),key);
            prepareAttempt(state,attempt,fingerprint);
            Long before=state.getVersion(); state.setCurrentAttempt(attempt); commit(state,before);
            return presenter.describe(attempt);
        }
        TopicDetailResponse detail=catalog.detail(topicId);
        TopicItemSummaryResponse item=detail.getItems().stream().filter(v->v.getTopicItemId().equals(itemId)).findFirst()
                .orElseThrow(()->problem(404,"CONTENT_UNAVAILABLE","Item is unavailable"));
        if(!"COMPLETED".equals(item.getLearningStatus())) throw problem(422,"NOT_COMPLETED","Complete this item before retrying");
        requireLevel(detail);
        if(active) throw problem(409,"ITEM_RETRY_IN_PROGRESS","Finish the active retry session first");
        List<AttemptItemSnapshotVo> snapshots=List.of(AttemptItemSnapshotVo.builder().topicItemId(itemId)
                .topicItemVersionId(item.getPublishedVersionId()).orderIndex(0).build());
        String sessionId=hash(owner()+"|session-item|"+itemId+"|"+key+scope(generation));
        TopicRetryRuntime updated=fresh(state,topicId,sessionId,"ITEM",snapshots,generation);
        UserTopicItemAttempt attempt=newAttempt(attemptId,updated,itemId,item.getPublishedVersionId(),key);
        updated.setCurrentAttempt(attempt);prepareSession(updated);prepareAttempt(updated,attempt,fingerprint);
        commit(updated,state==null?null:state.getVersion());
        return presenter.describe(attempt);
    }

    public TopicRetrySessionResponse startTopic(String topicId,StartTopicRetryRequest body,String key) {
        String sessionId=hash(owner()+"|session-topic|"+topicId+"|"+key);
        TopicRetryLocator replay=mongo.findById(sessionId,TopicRetryLocator.class);
        String fingerprint="topic|"+topicId+"|"+body.replaceRetrySessionId();
        if(replay!=null) {
            if(!owner().equals(replay.getUserId()) || !fingerprint.equals(replay.getFingerprint())) throw reused();
            requireGeneration(replay.getUserId(),topicId,replay.getGeneration());
            TopicRetryRuntime current=runtime(topicId,replay.getGeneration());
            if(current==null || !sessionId.equals(current.getSessionId())) throw changed();
            return session(current);
        }
        throw problem(410,"TOPIC_RETRY_RETIRED","Use the confirmed topic reset operation");
    }

    public TopicRetrySessionResponse getSession(String id) {
        TopicRetryLocator locator=locator(id,"SESSION");TopicRetryRuntime state=runtime(locator.getTopicId(),locator.getGeneration());
        if(state==null || !id.equals(state.getSessionId())) throw changed();
        return session(state);
    }

    public TopicAttemptResponse advance(String id,AdvanceTopicContentRequest body,String key) {
        TopicRetryRuntime state=forMutation(id);UserTopicItemAttempt attempt=find(state,id);
        String fingerprint="advance|"+body.revision()+"|"+body.stage()+"|"+body.itemIndex();
        ContentMutationReceiptVo replay=receipt(attempt,key,fingerprint);if(replay!=null)return receiptDto(attempt,replay);
        requireRunning(state,attempt,body.revision());
        var source=attempt.getFurthestReached();
        if(source.getStage()!=body.stage() || source.getItemIndex()!=body.itemIndex()) throw stateChanged();
        var pinned=version(attempt);var content=TopicAttemptContentMapper.content(pinned);var positions=presenter.positions(content);
        int index=positions.indexOf(new TopicCatalogPolicy.Position(body.stage().name(),body.itemIndex()));
        if(index<0) throw problem(409,"INVALID_STAGE","Only content can be advanced");
        TopicCatalogPolicy.Position next;
        if(index+1<positions.size()) next=positions.get(index+1);
        else {
            LearningStage quizStage=TopicAttemptService.firstQuizStage(pinned);
            if(quizStage==null) throw problem(422,"CONTENT_UNAVAILABLE","This item has no published supported exercise");
            next=new TopicCatalogPolicy.Position(quizStage.name(),0);
        }
        Long before=state.getVersion();Instant now=now();
        attempt.setFurthestReached(FurthestReachedVo.builder().stage(LearningStage.valueOf(next.stage())).itemIndex(next.itemIndex()).build());
        record(attempt,key,fingerprint,now,true);commit(state,before);return presenter.describe(attempt);
    }

    public QuizAnswerResponse answer(String id,String quizId,AnswerQuizRequest body,String key) {
        TopicRetryRuntime state=forMutation(id);UserTopicItemAttempt attempt=find(state,id);
        String fingerprint="answer|"+body.revision()+"|"+quizId+"|"+body.optionId();
        ContentMutationReceiptVo replay=receipt(attempt,key,fingerprint);
        var sequence=quiz.sequence(version(attempt));
        var question=quiz.find(sequence,quizId).orElseThrow(()->problem(404,"CONTENT_UNAVAILABLE","Quiz not found"));
        if(replay!=null) {
            boolean correct=body.optionId().equals(quiz.correctOptionId(question));
            return new QuizAnswerResponse(quizId,correct,correct?quiz.feedback(question):null,receiptDto(attempt,replay));
        }
        requireRunning(state,attempt,body.revision());
        if(attempt.getFurthestReached().getStage()!=LearningStage.FILL_BLANK && attempt.getFurthestReached().getStage()!=LearningStage.SITUATIONAL_CHOICE) throw stateChanged();
        var current=TopicAttemptService.firstUnanswered(sequence,attempt.getQuizProgress()).orElseThrow(this::stateChanged);
        if(!current.quizId().equals(quizId)) throw stateChanged();
        String prefix=quizId+":option:";boolean valid=false;
        for(int i=0;i<question.question().getOptions().size();i++) if((prefix+i).equals(body.optionId())) valid=true;
        if(!valid) throw problem(400,"INVALID_REQUEST","Option does not belong to this quiz");
        boolean correct=body.optionId().equals(quiz.correctOptionId(question));
        QuizFeedbackResponse feedback=correct?quiz.feedback(question):null;
        Long before=state.getVersion();Instant now=now();
        if(attempt.getQuizProgress()==null) attempt.setQuizProgress(new ArrayList<>());
        QuizProgressVo progress=attempt.getQuizProgress().stream().filter(p->p.getQuizId().equals(quizId)).findFirst().orElse(null);
        if(progress==null){progress=QuizProgressVo.builder().quizId(quizId).submissions(new ArrayList<>()).build();attempt.getQuizProgress().add(progress);}
        progress.getSubmissions().add(QuizSubmissionVo.builder().answer(body.optionId()).isCorrect(correct).submittedAt(now).build());
        if(correct) progress.setCompletedAt(now);
        var remaining=TopicAttemptService.firstUnanswered(sequence,attempt.getQuizProgress());
        if(correct && remaining.isEmpty()) {
            int firstTry=0;
            for(var entry:sequence){var p=attempt.getQuizProgress().stream().filter(v->v.getQuizId().equals(entry.quizId())).findFirst().orElseThrow();if(Boolean.TRUE.equals(p.getSubmissions().get(0).getIsCorrect()))firstTry++;}
            attempt.setStatus(AttemptStatus.COMPLETED);attempt.setFurthestReached(null);attempt.setCompletedAt(now);
            attempt.setCompletionResult(new CompletionResultVo(version(attempt).getTitle(),firstTry,sequence.size()));
            archive(state.getOfficialResults().get(attempt.getTopicItemId()),state.getVersion());
            state.getOfficialResults().put(attempt.getTopicItemId(),attempt);
            state.getCompletedItemIds().add(attempt.getTopicItemId());state.setCurrentAttempt(null);
            if(next(state)==null){state.setStatus("COMPLETED");state.setCompletedAt(now);}
        } else if(correct) {
            var next=remaining.orElseThrow();attempt.setFurthestReached(FurthestReachedVo.builder()
                    .stage(next.type()==QuizType.FILL_BLANK?LearningStage.FILL_BLANK:LearningStage.SITUATIONAL_CHOICE).itemIndex(next.indexInType()).build());
        }
        record(attempt,key,fingerprint,now,true);commit(state,before);
        return new QuizAnswerResponse(quizId,correct,feedback,presenter.describe(attempt));
    }

    public Map<String,Instant> access(String id,String key) {
        TopicRetryRuntime state=forMutation(id);UserTopicItemAttempt attempt=find(state,id);
        ContentMutationReceiptVo replay=receipt(attempt,key,"access");if(replay!=null)return Map.of("lastAccessedAt",replay.accessedAt());
        Long before=state.getVersion();Instant now=now();record(attempt,key,"access",now,false);commit(state,before);
        return Map.of("lastAccessedAt",now);
    }

    public static TopicRetrySessionResponse session(TopicRetryRuntime state) {
        return new TopicRetrySessionResponse(state.getSessionId(),state.getTopicId(),state.getScope(),state.getStatus(),
                state.getItemsSnapshot().stream().map(AttemptItemSnapshotVo::getTopicItemId).toList(),
                state.getCurrentAttempt()==null?null:state.getCurrentAttempt().getId(),state.getStartedAt(),state.getCompletedAt(),
                List.copyOf(state.getCompletedItemIds()),state.getItemsSnapshot().stream().map(v->new TopicRetrySessionResponse.Snapshot(v.getTopicItemId(),v.getTopicItemVersionId(),v.getOrderIndex())).toList(),next(state));
    }
    public static String next(TopicRetryRuntime state) {
        return state.getItemsSnapshot().stream().map(AttemptItemSnapshotVo::getTopicItemId).filter(id->!state.getCompletedItemIds().contains(id)).findFirst().orElse(null);
    }
    public TopicRetryRuntime runtime(String topicId) { return runtime(topicId,generation(topicId)); }
    TopicRetryRuntime runtime(String topicId,long generation) {
        return mongo.findById(hash(owner()+"|retry-runtime|"+topicId+scope(generation)),TopicRetryRuntime.class);
    }

    private TopicRetryRuntime forMutation(String id) {
        TopicRetryLocator locator=locator(id,"ATTEMPT");TopicRetryRuntime state=runtime(locator.getTopicId(),locator.getGeneration());
        UserTopicItemAttempt attempt=find(state,id);
        if(attempt==null || !locator.getSessionId().equals(state.getSessionId()) && attempt.getStatus()!=AttemptStatus.COMPLETED) throw changed();
        visible(attempt);return state;
    }
    private UserTopicItemAttempt find(TopicRetryRuntime state,String id) {
        if(state==null)return null;
        if(state.getCurrentAttempt()!=null && id.equals(state.getCurrentAttempt().getId()))return state.getCurrentAttempt();
        return state.getOfficialResults().values().stream().filter(v->id.equals(v.getId())).findFirst().orElse(null);
    }
    private TopicRetryLocator locator(String id,String kind) {
        var value=mongo.findById(id,TopicRetryLocator.class);
        if(value==null || !owner().equals(value.getUserId()) || !kind.equals(value.getKind()))throw problem(404,"CONTENT_UNAVAILABLE","Resource not found");
        requireGeneration(value.getUserId(),value.getTopicId(),value.getGeneration());
        return value;
    }
    private TopicAttemptResponse replayStart(TopicRetryLocator value,String fingerprint) {
        if(!owner().equals(value.getUserId()) || !fingerprint.equals(value.getFingerprint()))throw reused();
        requireGeneration(value.getUserId(),value.getTopicId(),value.getGeneration());
        TopicRetryRuntime current=runtime(value.getTopicId(),value.getGeneration());
        if(current==null || !value.getSessionId().equals(current.getSessionId()))throw changed();
        UserTopicItemAttempt live=find(current,value.getInitialAttempt()==null?value.getId():value.getInitialAttempt().getId());
        if(live==null)throw stateChanged();
        return presenter.describe(value.getInitialAttempt()==null?live:value.getInitialAttempt());
    }
    private void requireLevel(TopicDetailResponse detail) {
        if(learner.getCurrentLevel().ordinal()<detail.getCefrLevel().ordinal())throw problem(422,"LEVEL_TOO_LOW","Level is too low to create a retry");
    }
    private TopicRetryRuntime fresh(TopicRetryRuntime previous,String topicId,String sessionId,String scope,List<AttemptItemSnapshotVo> snapshots,long generation) {
        return TopicRetryRuntime.builder().id(hash(owner()+"|retry-runtime|"+topicId+scope(generation))).generation(generation).userId(owner()).topicId(topicId)
                .version(previous==null?null:previous.getVersion()).sessionId(sessionId).scope(scope).status("IN_PROGRESS")
                .startedAt(now()).itemsSnapshot(snapshots).completedItemIds(new ArrayList<>())
                .officialResults(previous==null?new HashMap<>():new HashMap<>(previous.getOfficialResults())).build();
    }
    private UserTopicItemAttempt newAttempt(String id,TopicRetryRuntime state,String itemId,String versionId,String key) {
        TopicItemVersion pinned=versions.findById(versionId).orElseThrow(()->problem(404,"CONTENT_UNAVAILABLE","Pinned version missing"));
        TopicAttemptContentMapper.content(pinned);Instant now=now();
        UserTopicItemAttempt attempt=UserTopicItemAttempt.builder().id(id).generation(state.getGeneration()).userId(owner()).topicId(state.getTopicId()).topicItemId(itemId)
                .topicItemVersionId(versionId).attemptType(AttemptType.RETRY).userTopicAttemptId(state.getSessionId()).version(0L)
                .status(AttemptStatus.IN_PROGRESS).furthestReached(FurthestReachedVo.builder().stage(LearningStage.CONTEXT).itemIndex(0).build())
                .startRequestKey(key).mutationReceipts(new ArrayList<>()).quizProgress(new ArrayList<>()).startedAt(now).lastAccessedAt(now).build();
        visible(attempt);return attempt;
    }
    private void prepareAttempt(TopicRetryRuntime state,UserTopicItemAttempt attempt,String fingerprint) {
        prepare(TopicRetryLocator.builder().id(attempt.getId()).generation(state.getGeneration()).startRequestKey(attempt.getStartRequestKey()).userId(owner()).topicId(state.getTopicId()).sessionId(state.getSessionId())
                .kind("ATTEMPT").fingerprint(fingerprint).initialAttempt(attempt).build());
    }
    private void prepareSession(TopicRetryRuntime state) {prepareSession(state,"item-session");}
    private void prepareSession(TopicRetryRuntime state,String fingerprint) {
        prepare(TopicRetryLocator.builder().id(state.getSessionId()).generation(state.getGeneration()).userId(owner()).topicId(state.getTopicId()).sessionId(state.getSessionId())
                .kind("SESSION").fingerprint(fingerprint).scope(state.getScope()).itemsSnapshot(state.getItemsSnapshot())
                .startedAt(state.getStartedAt()).initialAttemptId(state.getCurrentAttempt().getId()).build());
    }
    private void prepare(TopicRetryLocator locator) {
        try{mongo.insert(locator);}catch(DuplicateKeyException duplicate){
            var existing=mongo.findById(locator.getId(),TopicRetryLocator.class);
            if(existing==null || !Objects.equals(existing.getFingerprint(),locator.getFingerprint()) || !Objects.equals(existing.getUserId(),locator.getUserId()))throw reused();
        }
    }
    private void archive(UserTopicItemAttempt attempt,Long version) {
        if(attempt==null)return;
        TopicRetryArchive snapshot=TopicRetryArchive.builder().id(hash(attempt.getId()+"|archive|"+attempt.getVersion()))
                .generation(attempt.getGeneration()).userId(attempt.getUserId()).topicId(attempt.getTopicId()).sessionId(attempt.getUserTopicAttemptId())
                .sourceVersion(version).attempt(attempt).build();
        try{mongo.insert(snapshot);}catch(DuplicateKeyException existing){ }
    }
    private void commit(TopicRetryRuntime state,Long before) {
        requireGeneration(state.getUserId(),state.getTopicId(),state.getGeneration());
        if(before==null){try{mongo.insert(state);requireGeneration(state.getUserId(),state.getTopicId(),state.getGeneration());return;}catch(DuplicateKeyException conflict){throw changed();}}
        Update update=new Update().set("sessionId",state.getSessionId()).set("scope",state.getScope()).set("status",state.getStatus())
                .set("startedAt",state.getStartedAt()).set("completedAt",state.getCompletedAt()).set("itemsSnapshot",state.getItemsSnapshot())
                .set("completedItemIds",state.getCompletedItemIds()).set("currentAttempt",state.getCurrentAttempt()).set("officialResults",state.getOfficialResults()).set("version",before+1);
        var saved=mongo.findAndModify(new Query(Criteria.where("_id").is(state.getId()).and("userId").is(owner()).and("version").is(before)),update,
                FindAndModifyOptions.options().returnNew(true),TopicRetryRuntime.class);
        if(saved==null)throw changed();state.setVersion(before+1);
        requireGeneration(state.getUserId(),state.getTopicId(),state.getGeneration());
    }
    private void requireRunning(TopicRetryRuntime state,UserTopicItemAttempt attempt,long revision) {
        if(state.getCurrentAttempt()==null || !attempt.getId().equals(state.getCurrentAttempt().getId()) || !"IN_PROGRESS".equals(state.getStatus()) ||
                attempt.getStatus()!=AttemptStatus.IN_PROGRESS || attempt.getVersion()!=revision)throw stateChanged();
    }
    private void record(UserTopicItemAttempt attempt,String key,String fingerprint,Instant now,boolean increment) {
        if(increment)attempt.setVersion(attempt.getVersion()+1);attempt.setLastAccessedAt(now);
        var p=attempt.getFurthestReached();attempt.getMutationReceipts().add(new ContentMutationReceiptVo(key,fingerprint,p==null?null:p.getStage(),p==null?0:p.getItemIndex(),attempt.getVersion(),now,true));
    }
    private ContentMutationReceiptVo receipt(UserTopicItemAttempt attempt,String key,String fingerprint) {
        if(key.equals(attempt.getStartRequestKey()))throw reused();
        for(var receipt:attempt.getMutationReceipts())if(key.equals(receipt.key())){if(!fingerprint.equals(receipt.fingerprint()))throw reused();return receipt;}
        return null;
    }
    private TopicAttemptResponse receiptDto(UserTopicItemAttempt attempt,ContentMutationReceiptVo receipt) {
        return presenter.describe(attempt,receipt.stage(),receipt.itemIndex(),receipt.revision(),receipt.accessedAt());
    }
    private TopicItemVersion version(UserTopicItemAttempt attempt){return versions.findById(attempt.getTopicItemVersionId()).orElseThrow(()->problem(404,"CONTENT_UNAVAILABLE","Pinned version missing"));}
    private void visible(UserTopicItemAttempt attempt) {
        TopicItemVersion pinned=version(attempt);
        Topic topic=topics.findById(attempt.getTopicId()).orElseThrow(()->problem(410,"ITEM_HIDDEN","Content unavailable"));
        TopicVersion release=topic.getLatestPublishedVersion()==null?null:releases.findById(topic.getLatestPublishedVersion()).orElse(null);
        if(pinned.getStatus()==ContentStatus.HIDDEN || release==null || release.getStatus()!=ContentStatus.PUBLISHED || release.getItemVersionIds()==null ||
                versions.findAllById(release.getItemVersionIds()).stream().noneMatch(v->attempt.getTopicItemId().equals(v.getTopicItemId()) && v.getStatus()!=ContentStatus.HIDDEN))
            throw problem(410,"ITEM_HIDDEN","Content unavailable");
    }
    private String owner(){String value=learner.getCurrentUserId();if(value==null)throw problem(401,"SIGN_IN_REQUIRED","Select a local learner");return value;}
    private long generation(String topicId){return generations==null?0:generations.current(owner(),topicId);}
    private String scope(long generation){return generation==0?"":"|generation|"+generation;}
    private void requireGeneration(String owner,String topicId,long generation){if(generations!=null)generations.require(owner,topicId,generation);}
    private Instant now(){return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(Exception e){throw new IllegalStateException(e);}}
    private TopicRequestException changed(){return problem(409,"RETRY_SESSION_CHANGED","The retry session changed; reload the topic");}
    private TopicRequestException stateChanged(){return problem(409,"STATE_CHANGED","Reload the saved retry position");}
    private TopicRequestException reused(){return problem(409,"IDEMPOTENCY_KEY_REUSED","Key already used for another action");}
    private TopicRequestException problem(int status,String code,String detail){return new TopicRequestException(status,code,detail);}
}
