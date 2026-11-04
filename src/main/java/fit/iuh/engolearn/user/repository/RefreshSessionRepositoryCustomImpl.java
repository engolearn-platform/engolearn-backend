package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.RefreshSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

@RequiredArgsConstructor
public class RefreshSessionRepositoryCustomImpl implements RefreshSessionRepositoryCustom {
    private final MongoTemplate mongo;

    @Override
    public boolean rotateIfCurrent(String sessionId, String accountId, String currentHash, String nextHash, Instant now) {
        Query query = Query.query(Criteria.where("session_id").is(sessionId)
                .and("account_id").is(accountId).and("token_hash").is(currentHash)
                .and("revoked").is(false).and("expires_at").gt(now));
        return mongo.updateFirst(query, new Update().set("token_hash", nextHash).inc("generation", 1), RefreshSession.class)
                .getModifiedCount() == 1;
    }

    @Override
    public boolean revokeIfOwnedBy(String sessionId, String accountId) {
        Query query = Query.query(Criteria.where("session_id").is(sessionId)
                .and("account_id").is(accountId).and("revoked").is(false));
        return mongo.updateFirst(query, new Update().set("revoked", true), RefreshSession.class).getModifiedCount() == 1;
    }
}
