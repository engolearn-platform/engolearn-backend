package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionMessage;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoCompanionMessageRepository implements CompanionMessageRepository {
    private final MongoTemplate mongo;
    @Override public CompanionMessage insert(CompanionMessage message) { return mongo.insert(message); }
    @Override public Optional<CompanionMessage> findByTurnAndRole(String conversationId, String turnId, CompanionMessage.Role role) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("conversation_id").is(conversationId)
                .and("turn_id").is(turnId).and("role").is(role)), CompanionMessage.class));
    }
    @Override public List<CompanionMessage> newest(String conversationId, Long beforeSequence, int limit) {
        Criteria criteria = Criteria.where("conversation_id").is(conversationId);
        if (beforeSequence != null) criteria.and("sequence").lt(beforeSequence);
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "sequence")).limit(limit), CompanionMessage.class);
    }
    @Override public void deleteConversationMessages(String conversationId) {
        mongo.remove(Query.query(Criteria.where("conversation_id").is(conversationId)), CompanionMessage.class);
    }
}
