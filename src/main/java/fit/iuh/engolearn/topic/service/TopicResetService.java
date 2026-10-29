package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.ResetTopicRequest;
import fit.iuh.engolearn.topic.dto.response.ResetTopicResponse;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.TopicLearnerState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

@Service
public class TopicResetService {
    private final LocalLearnerProvider learner;
    private final TopicLearnerCatalogService catalog;
    private final TopicGenerationService generations;
    private final MongoTemplate mongo;
    public TopicResetService(LocalLearnerProvider learner, TopicLearnerCatalogService catalog,
            TopicGenerationService generations, MongoTemplate mongo) {
        this.learner = learner; this.catalog = catalog; this.generations = generations; this.mongo = mongo;
    }
    public ResetTopicResponse reset(String topicId, ResetTopicRequest body, String key) {
        String owner = learner.getCurrentUserId();
        if (owner == null) throw problem(401, "SIGN_IN_REQUIRED", "Select a local learner");
        String id = generations.id(owner, topicId);
        String fingerprint = "reset|" + topicId + "|" + body.generation();
        TopicLearnerState state = mongo.findById(id, TopicLearnerState.class);
        ResetTopicResponse replay = replay(state, key, fingerprint);
        if (replay != null) return replay;
        if ((state == null ? 0 : state.getGeneration()) != body.generation()) throw changed();
        fit.iuh.engolearn.topic.dto.response.TopicDetailResponse detail;
        try { detail = catalog.detail(topicId); }
        catch (fit.iuh.engolearn.topic.exception.TopicNotFoundException missing) {
            throw problem(404, "CONTENT_UNAVAILABLE", "Published topic not found");
        }
        if (!"COMPLETED".equals(detail.getProgress().status()) || detail.getItems().isEmpty())
            throw problem(422, "NOT_COMPLETED", "Complete this topic before resetting");
        if (learner.getCurrentLevel().ordinal() < detail.getCefrLevel().ordinal())
            throw problem(422, "LEVEL_TOO_LOW", "Level is too low to reset this topic");
        if (state == null) {
            state = TopicLearnerState.builder().id(id).userId(owner).topicId(topicId).generation(0).receipts(new ArrayList<>()).build();
            try { mongo.insert(state); } catch (DuplicateKeyException concurrent) {
                state = mongo.findById(id, TopicLearnerState.class);
                replay = replay(state, key, fingerprint);
                if (replay != null) return replay;
            }
        }
        var receipt = new TopicLearnerState.ResetReceipt(key, fingerprint, body.generation(), body.generation() + 1);
        var saved = mongo.findAndModify(new Query(Criteria.where("_id").is(id).and("generation").is(body.generation())),
                new Update().inc("generation", 1).push("receipts", receipt), FindAndModifyOptions.options().returnNew(true), TopicLearnerState.class);
        if (saved == null) {
            replay = replay(mongo.findById(id, TopicLearnerState.class), key, fingerprint);
            if (replay != null) return replay;
            throw changed();
        }
        return new ResetTopicResponse(topicId, saved.getGeneration());
    }
    private ResetTopicResponse replay(TopicLearnerState state, String key, String fingerprint) {
        if (state != null && state.getReceipts() != null) for (var receipt : state.getReceipts()) {
            if (!key.equals(receipt.key())) continue;
            if (!fingerprint.equals(receipt.fingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another reset");
            return new ResetTopicResponse(state.getTopicId(), receipt.generation());
        }
        return null;
    }
    private TopicRequestException changed() { return problem(409, "STATE_CHANGED", "Topic changed; reload before resetting"); }
    private TopicRequestException problem(int status, String code, String detail) { return new TopicRequestException(status, code, detail); }
}
