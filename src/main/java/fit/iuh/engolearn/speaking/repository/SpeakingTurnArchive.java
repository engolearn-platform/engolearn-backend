package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingTurnStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingChargeVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingMediaVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpeakingTurnArchive {
    private final MongoTemplate mongo;

    public void attachLearnerAudio(String sessionId, String turnId, String token, SpeakingMediaVo media, Instant now) {
        var query = Query.query(identity(sessionId, turnId).and("value.evidence_token").is(token)
                .and("value.learner_audio").isNull());
        mongo.updateFirst(query, new Update().set("value.learner_audio", media).set("updated_at", now), SpeakingTurn.class);
    }

    public void stageAssistantAudio(String sessionId, String turnId, String token, SpeakingMediaVo media, Instant now) {
        var query = Query.query(identity(sessionId, turnId).and("value.delivery_token").is(token)
                .and("value.delivery_deadline").gt(now).and("value.audio_status").is(SpeakingAudioStatus.AUDIO_PENDING));
        mongo.updateFirst(query, new Update().set("value.assistant_audio", media).set("updated_at", now),
                SpeakingTurn.class);
    }

    public boolean hasPendingAudio(String sessionId) {
        return mongo.exists(Query.query(Criteria.where("session_id").is(sessionId)
                .and("value.audio_status").is(SpeakingAudioStatus.AUDIO_PENDING)), SpeakingTurn.class);
    }

    public boolean deliveryCurrent(String sessionId, String turnId, String token, Instant now) {
        return mongo.exists(Query.query(identity(sessionId, turnId).and("value.delivery_token").is(token)
                .and("value.audio_status").is(SpeakingAudioStatus.AUDIO_PENDING).and("value.delivery_deadline").gt(now)),
                SpeakingTurn.class);
    }

    public void deferCharge(SpeakingTurn row, Instant now) {
        var query = Query.query(identity(row.getSessionId(), row.getId()).and("value.revision").is(row.getValue().getRevision()));
        mongo.updateFirst(query, new Update().set("updated_at", now), SpeakingTurn.class);
    }

    public boolean markReplyDelivered(String sessionId, String turnId, String token, Instant sentAt) {
        var query = Query.query(identity(sessionId, turnId).and("value.delivery_token").is(token)
                .and("value.reply_delivered_at").isNull());
        return mongo.updateFirst(query, new Update().set("value.reply_delivered_at", sentAt),
                SpeakingTurn.class).getModifiedCount() == 1;
    }

    public boolean finishAudio(String sessionId, String turnId, String token, SpeakingAudioStatus status,
                               SpeakingMediaVo media, long durationMs, Instant now, boolean expired) {
        var match = identity(sessionId, turnId).and("value.delivery_token").is(token)
                .and("value.audio_status").is(SpeakingAudioStatus.AUDIO_PENDING);
        if (expired) match.and("value.delivery_deadline").lte(now);
        else match.and("value.delivery_deadline").gt(now);
        var update = new Update().set("value.audio_status", status).set("value.assistant_audio", media)
                .set("value.delivered_audio_ms", durationMs).unset("value.delivery_token")
                .unset("value.delivery_deadline").set("updated_at", now);
        return mongo.updateFirst(Query.query(match), update, SpeakingTurn.class).getModifiedCount() == 1;
    }

    public List<SpeakingTurn> pendingChargesForOwner(String ownerId, Instant now, int limit) {
        var ready = new Criteria().orOperator(Criteria.where("value.audio_status").ne(SpeakingAudioStatus.AUDIO_PENDING),
                Criteria.where("value.delivery_deadline").lte(now));
        var current = Query.query(new Criteria().andOperator(ready, pendingChargeCriteria().and("owner_id").is(ownerId)))
                .with(Sort.by("updated_at", "_id")).limit(limit);
        List<SpeakingTurn> result = mongo.find(current, SpeakingTurn.class);
        if (result.size() < limit) result.addAll(legacyPendingCharges(ownerId, now, limit - result.size()));
        return result;
    }

    public boolean hasPendingChargesForOwner(String ownerId) {
        Query current = Query.query(pendingChargeCriteria().and("owner_id").is(ownerId)).limit(1);
        return mongo.exists(current, SpeakingTurn.class) || !legacyPendingCharges(ownerId, null, 1).isEmpty();
    }

    private Criteria pendingChargeCriteria() {
        return Criteria.where("quota_recorded").ne(true).and("value.sealed_at").exists(true).ne(null)
                .and("value.status").in(SpeakingTurnStatus.COMMITTED, SpeakingTurnStatus.FAILED, SpeakingTurnStatus.SKIPPED);
    }

    private List<SpeakingTurn> legacyPendingCharges(String ownerId, Instant now, int limit) {
        if (limit <= 0) return List.of();
        Criteria legacy = new Criteria().andOperator(pendingChargeCriteria(), new Criteria().orOperator(
                Criteria.where("owner_id").exists(false), Criteria.where("owner_id").is(null)));
        Aggregation pipeline;
        if (now == null) {
            pipeline = Aggregation.newAggregation(Aggregation.match(legacy),
                    Aggregation.lookup(mongo.getCollectionName(fit.iuh.engolearn.speaking.model.SpeakingSession.class),
                            "session_id", "_id", "session"), Aggregation.unwind("session"),
                    Aggregation.match(Criteria.where("session.owner_id").is(ownerId)), Aggregation.limit(limit));
        } else {
            Criteria ready = new Criteria().orOperator(Criteria.where("value.audio_status").ne(SpeakingAudioStatus.AUDIO_PENDING),
                    Criteria.where("value.delivery_deadline").lte(now));
            pipeline = Aggregation.newAggregation(Aggregation.match(new Criteria().andOperator(legacy, ready)),
                    Aggregation.lookup(mongo.getCollectionName(fit.iuh.engolearn.speaking.model.SpeakingSession.class),
                            "session_id", "_id", "session"), Aggregation.unwind("session"),
                    Aggregation.match(Criteria.where("session.owner_id").is(ownerId)),
                    Aggregation.sort(Sort.by("updated_at", "_id")), Aggregation.limit(limit));
        }
        return mongo.aggregate(pipeline, mongo.getCollectionName(SpeakingTurn.class), SpeakingTurn.class).getMappedResults();
    }

    public List<SpeakingTurn> pendingCharges(Instant now, int limit) {
        var ready = new Criteria().orOperator(Criteria.where("value.audio_status").ne(SpeakingAudioStatus.AUDIO_PENDING),
                Criteria.where("value.delivery_deadline").lte(now));
        var query = Query.query(new Criteria().andOperator(ready, Criteria.where("quota_recorded").ne(true)
                .and("value.sealed_at").exists(true).ne(null).and("value.status")
                .in(SpeakingTurnStatus.COMMITTED, SpeakingTurnStatus.FAILED, SpeakingTurnStatus.SKIPPED)))
                .with(Sort.by("updated_at", "_id")).limit(limit);
        return mongo.find(query, SpeakingTurn.class);
    }

    public SpeakingChargeVo prepareCharge(SpeakingTurn row, SpeakingChargeVo charge, Instant now) {
        var query = Query.query(identity(row.getSessionId(), row.getId()).and("value.revision")
                .is(row.getValue().getRevision()).and("charge").isNull());
        var prepared = mongo.findAndModify(query, new Update().set("charge", charge).set("updated_at", now),
                FindAndModifyOptions.options().returnNew(true), SpeakingTurn.class);
        if (prepared == null) prepared = mongo.findById(row.getId(), SpeakingTurn.class);
        return prepared == null ? null : prepared.getCharge();
    }

    public void confirmCharge(String sessionId, String turnId, String ownerId, String ledgerId, Instant recordedAt, Instant now) {
        Update update = new Update().set("owner_id", ownerId).set("quota_recorded", true).set("updated_at", now);
        if (ledgerId != null) update.set("ledger_id", ledgerId);
        if (recordedAt != null) update.set("recorded_at", recordedAt);
        mongo.updateFirst(Query.query(identity(sessionId, turnId).and("charge").ne(null)), update, SpeakingTurn.class);
    }

    private Criteria identity(String sessionId, String turnId) {
        return Criteria.where("_id").is(turnId).and("session_id").is(sessionId);
    }

    public void record(String sessionId, String ownerId, SpeakingTurnVo turn, Instant now) {
        Query previous = Query.query(Criteria.where("_id").is(turn.getTurnId()).and("session_id").is(sessionId)
                .and("value.revision").lt(turn.getRevision()));
        Update update = new Update().setOnInsert("session_id", sessionId).setOnInsert("created_at", now)
                .setOnInsert("owner_id", ownerId).set("value", turn).set("updated_at", now);
        try {
            mongo.upsert(previous, update, SpeakingTurn.class);
        } catch (DuplicateKeyException collision) {
            SpeakingTurn existing = mongo.findById(turn.getTurnId(), SpeakingTurn.class);
            if (existing == null || !sessionId.equals(existing.getSessionId())
                    || existing.getValue().getRevision() < turn.getRevision()) throw collision;
        }
        mongo.updateFirst(Query.query(identity(sessionId, turn.getTurnId())),
                new Update().set("owner_id", ownerId), SpeakingTurn.class);
    }
}
