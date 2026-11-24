package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class SpeakingTurnArchive {
    private final MongoTemplate mongo;

    public void record(String sessionId, SpeakingTurnVo turn, Instant now) {
        Query previous = Query.query(Criteria.where("_id").is(turn.getTurnId()).and("session_id").is(sessionId)
                .and("value.revision").lt(turn.getRevision()));
        Update update = new Update().setOnInsert("session_id", sessionId).setOnInsert("created_at", now)
                .set("value", turn).set("updated_at", now);
        try {
            mongo.upsert(previous, update, SpeakingTurn.class);
        } catch (DuplicateKeyException collision) {
            SpeakingTurn existing = mongo.findById(turn.getTurnId(), SpeakingTurn.class);
            if (existing == null || !sessionId.equals(existing.getSessionId())
                    || existing.getValue().getRevision() < turn.getRevision()) throw collision;
        }
    }
}
