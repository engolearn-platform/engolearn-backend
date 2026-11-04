package fit.iuh.engolearn.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAuthIndexRepository {
    private final MongoTemplate mongo;

    public void ensureUniqueIndexes() {
//        mongo.indexOps("users").ensureIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email"));
//        mongo.indexOps("refresh_sessions").ensureIndex(new Index().on("session_id", Sort.Direction.ASC).unique().named("session_id"));
    }
}
