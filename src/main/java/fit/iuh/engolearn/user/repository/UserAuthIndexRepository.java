package fit.iuh.engolearn.user.repository;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAuthIndexRepository {
    private final MongoTemplate mongo;

    public void ensureUniqueIndexes() {
        var collection = mongo.getCollection("users");
        // Check legacy data without logging email addresses or reading credentials.
        var projection = new Document("email", 1).append("admin_revision", 1).append("session_epoch", 1);
        try (var rows = collection.find().projection(projection).iterator()) {
            while (rows.hasNext()) {
                var row = rows.next();
                var id = row.get("_id");
                if (!(id instanceof ObjectId) && !(id instanceof String text && !ObjectId.isValid(text))) {
                    throw new IllegalStateException("Resolve unsupported users._id representation before startup");
                }
                checkCounter(row, "admin_revision");
                checkCounter(row, "session_epoch");
                Object value = row.get("email");
                if (!(value instanceof String email) || email.isBlank()
                        || !email.equals(email.trim().toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalStateException("Resolve noncanonical users.email values before startup");
                }
            }
        }
        var key = new org.bson.Document("email", 1);
        boolean equivalent = false;
        for (var index : collection.listIndexes()) {
            if (key.equals(index.get("key")) && Boolean.TRUE.equals(index.get("unique"))
                    && !Boolean.TRUE.equals(index.get("sparse")) && !index.containsKey("partialFilterExpression")
                    && !index.containsKey("collation")) equivalent = true;
        }
        if (!equivalent) mongo.indexOps("users").ensureIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email"));
        if (!mongo.collectionExists("user_management_audit")) mongo.createCollection("user_management_audit");
    }

    private static void checkCounter(Document row, String field) {
        if (!row.containsKey(field)) return;
        Object value = row.get(field);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
            throw new IllegalStateException("Resolve invalid users." + field + " before startup");
        }
    }
}
