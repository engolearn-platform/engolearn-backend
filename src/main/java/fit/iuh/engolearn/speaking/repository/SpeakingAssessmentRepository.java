package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingAssessmentVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingLanguageAssessmentVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingWordAssessmentVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus.*;

@Repository
@RequiredArgsConstructor
public class SpeakingAssessmentRepository {
    private final MongoTemplate mongo;

    public List<SpeakingSession> workAfter(String after, int count) {
        Criteria match = Criteria.where("deleted_at").isNull()
                .and("assessment_status").in(SpeakingAssessmentStatus.PENDING, SpeakingAssessmentStatus.FINALIZING);
        if (after != null) match.and("_id").gt(after);
        return mongo.find(Query.query(match).with(Sort.by("_id")).limit(count), SpeakingSession.class);
    }

    public List<SpeakingAudioAssessment> samples(String sessionId) {
        return mongo.find(Query.query(Criteria.where("session_id").is(sessionId))
                .with(Sort.by("sequence")), SpeakingAudioAssessment.class);
    }

    public SpeakingAudioAssessment register(String sessionId, String turnId, int sequence, Instant now,
                                             String policyVersion, String locale) {
        Query identity = Query.query(audioIdentity(sessionId, turnId));
        Update initial = new Update().setOnInsert("status", PENDING).setOnInsert("sequence", sequence)
                .setOnInsert("provider", "AZURE_SPEECH").setOnInsert("locale", locale).setOnInsert("policy_version", policyVersion)
                .setOnInsert("attempt_count", 0).setOnInsert("created_at", now).setOnInsert("updated_at", now);
        try {
            return mongo.findAndModify(identity, initial, FindAndModifyOptions.options().returnNew(true).upsert(true),
                    SpeakingAudioAssessment.class);
        } catch (DuplicateKeyException concurrent) {
            var winner = mongo.findOne(identity, SpeakingAudioAssessment.class);
            if (winner == null) throw concurrent;
            return winner;
        }
    }

    public SpeakingAudioAssessment claim(String sessionId, String turnId, String instance, String token,
                                          Instant now, Instant deadline, int attemptLimit, String policyVersion) {
        Criteria match = audioIdentity(sessionId, turnId).and("status").is(PENDING).and("attempt_count").lt(attemptLimit);
        Update start = new Update().set("status", PROCESSING).set("token", token).set("worker_instance", instance)
                .set("claim_deadline", deadline).set("policy_version", policyVersion)
                .set("updated_at", now).inc("attempt_count", 1);
        return mongo.findAndModify(Query.query(match), start, FindAndModifyOptions.options().returnNew(true),
                SpeakingAudioAssessment.class);
    }

    public void returnToQueue(SpeakingAudioAssessment claim, Instant now) {
        mongo.updateFirst(Query.query(claimIdentity(claim)), clearClaim(PENDING, now), SpeakingAudioAssessment.class);
    }

    public void rejectPending(String sessionId, String turnId, String reason, Instant now) {
        Criteria match = audioIdentity(sessionId, turnId).and("status").is(PENDING);
        mongo.updateFirst(Query.query(match), clearClaim(FAILED, now).set("reason", reason).set("finished_at", now),
                SpeakingAudioAssessment.class);
    }

    public void finish(SpeakingAudioAssessment claim, SpeakingAudioAssessmentStatus status, String reason, Instant now,
                       long preparedDurationMs, int words, Double pronunciation, String pronunciationReason, Double fluency,
                       String fluencyReason, List<SpeakingWordAssessmentVo> wordFeedback, long assessedSpeechDurationMs,
                       long fluencyEvidenceDurationMs) {
        Criteria match = claimIdentity(claim);
        if (status == ASSESSED) match.and("claim_deadline").gt(now);
        Update terminal = clearClaim(status, now).set("finished_at", now).set("reason", reason)
                .set("duration_ms", preparedDurationMs).set("words", words).set("pronunciation", pronunciation)
                .set("pronunciation_reason", pronunciationReason).set("fluency", fluency)
                .set("fluency_reason", fluencyReason).set("word_feedback", wordFeedback);
        if (assessedSpeechDurationMs > 0) terminal.set("assessed_speech_duration_ms", assessedSpeechDurationMs);
        if (fluencyEvidenceDurationMs > 0) terminal.set("fluency_evidence_duration_ms", fluencyEvidenceDurationMs);
        mongo.updateFirst(Query.query(match), terminal, SpeakingAudioAssessment.class);
    }

    public void recoverClaim(SpeakingAudioAssessment claim, int attemptLimit, Instant now, String reason) {
        if (claim.getAttemptCount() < attemptLimit) returnToQueue(claim, now);
        else finish(claim, FAILED, reason, now, 0, 0, null, reason, null, reason, List.of(), 0, 0);
    }

    public boolean storeLanguage(String sessionId, SpeakingLanguageAssessmentVo language) {
        var match = finalizing(sessionId).and("language_assessment").isNull();
        return mongo.updateFirst(Query.query(match), new Update().set("language_assessment", language)
                .inc("revision", 1), SpeakingSession.class).getModifiedCount() == 1;
    }

    public boolean startLanguage(String sessionId, Instant deadline) {
        var match = finalizing(sessionId).and("assessment_language_deadline").isNull().and("language_assessment").isNull();
        return mongo.updateFirst(Query.query(match), new Update().set("assessment_language_deadline", deadline)
                .inc("revision", 1), SpeakingSession.class).getModifiedCount() == 1;
    }

    public boolean publish(String sessionId, SpeakingAssessmentStatus status, SpeakingAssessmentVo assessment) {
        var match = finalizing(sessionId).and("assessment").isNull().and("language_assessment").ne(null);
        var update = new Update().set("assessment", assessment).set("assessment_status", status)
                .unset("language_assessment").inc("revision", 1);
        return mongo.updateFirst(Query.query(match), update, SpeakingSession.class).getModifiedCount() == 1;
    }

    private static Criteria finalizing(String sessionId) {
        return Criteria.where("_id").is(sessionId).and("deleted_at").isNull().and("status").is(SpeakingSessionStatus.ENDED)
                .and("assessment_status").is(SpeakingAssessmentStatus.FINALIZING);
    }

    private static Criteria audioIdentity(String sessionId, String turnId) {
        return Criteria.where("_id").is(turnId).and("session_id").is(sessionId);
    }

    private static Criteria claimIdentity(SpeakingAudioAssessment claim) {
        return audioIdentity(claim.getSessionId(), claim.getId()).and("status").is(PROCESSING).and("token").is(claim.getToken());
    }

    private static Update clearClaim(SpeakingAudioAssessmentStatus status, Instant now) {
        return new Update().set("status", status).set("updated_at", now).unset("token")
                .unset("worker_instance").unset("claim_deadline");
    }
}
