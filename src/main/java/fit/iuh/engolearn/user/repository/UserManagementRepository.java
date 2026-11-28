package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.*;
import fit.iuh.engolearn.user.model.enums.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserManagementRepository {
    private final MongoTemplate mongo;

    public long countManagementAccounts() {
        return mongo.count(Query.query(Criteria.where("deleted_at").is(null)), User.class);
    }

    private List<Document> projected(Document filter) {
        var pipeline = new ArrayList<Document>();
        filter.put("deleted_at", null);
        pipeline.add(new Document("$match", filter));
        pipeline.add(new Document("$project", new Document("email", 1).append("role", new Document("$ifNull", List.of("$role", "USER")))
                .append("status", new Document("$cond", List.of(new Document("$eq", List.of("$locked", true)), "DISABLED", "ACTIVE")))
                .append("createdAt", "$created_at").append("revision", new Document("$ifNull", List.of("$admin_revision", 0)))));
        // The name collation also applies inside lookup. Match ID bytes to keep legacy
        // case-distinct string IDs isolated while retaining the indexed equality join.
        var profileId = new Document("$toString", "$_id");
        var exactId = new Document("$and", List.of(
                new Document("$eq", List.of(new Document("$strLenBytes", profileId), new Document("$strLenBytes", "$$accountId"))),
                new Document("$eq", List.of(new Document("$indexOfBytes", List.of(profileId, "$$accountId")), 0))));
        pipeline.add(new Document("$lookup", new Document("from", "user_profiles").append("localField", "_id")
                .append("foreignField", "_id").append("let", new Document("accountId", new Document("$toString", "$_id")))
                .append("pipeline", List.of(new Document("$match", new Document("$expr", exactId)),
                        new Document("$project", new Document("display_name", 1))))
                .append("as", "profiles")));
        pipeline.add(new Document("$set", new Document("name", new Document("$first", "$profiles.display_name"))));
        pipeline.add(new Document("$unset", "profiles"));
        return pipeline;
    }

    public UserRoster.Page list(UserRoster.Filter request) {
        var pipeline = projected(new Document());
        var match = new Document();
        if (request.role() != null) match.append("role", request.role().name());
        if (request.status() != null) match.append("status", request.status().name());
        if (!request.search().isEmpty()) {
            Pattern literal = Pattern.compile(Pattern.quote(request.search()), Pattern.CASE_INSENSITIVE);
            match.append("$or", List.of(new Document("name", literal), new Document("email", literal)));
        }
        pipeline.add(new Document("$match", match));
        boolean byName = request.sort().startsWith("name,");
        var value = byName ? "$name" : "$createdAt";
        // Hex preserves bytewise ID ordering under the name collation, including case-distinct IDs.
        var idBytes = new Document("$convert", new Document("input", new Document("$toString", "$_id"))
                .append("to", "binData").append("format", "utf8"));
        var tieId = new Document("$convert", new Document("input", idBytes).append("to", "string").append("format", "hex"));
        pipeline.add(new Document("$set", new Document("missing", new Document("$in", List.of(new Document("$type", value), List.of("missing","null"))))
                .append("sortValue", value).append("tieId", tieId)));
        pipeline.add(new Document("$sort", new Document("missing", 1)
                .append("sortValue", request.sort().endsWith(",asc") ? 1 : -1).append("tieId", 1)));
        pipeline.add(new Document("$facet", new Document("items", List.of(new Document("$skip", request.page()*request.size()),
                new Document("$limit", request.size()), new Document("$unset", List.of("revision","missing","sortValue","tieId"))))
                .append("total", List.of(new Document("$count", "count")))));
        var result = mongo.execute("users", collection -> {
            var rows = collection.aggregate(pipeline).allowDiskUse(true);
            if (byName) rows.collation(com.mongodb.client.model.Collation.builder().locale("vi")
                    .collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY).build());
            return rows.first();
        });
        var count = result.getList("total", Document.class);
        long total = count.isEmpty() ? 0 : ((Number) count.getFirst().get("count")).longValue();
        var items = result.getList("items", Document.class).stream().map(this::entry).toList();
        return new UserRoster.Page(items, total);
    }

    private Instant createdAt(Document row) {
        var date = row.getDate("createdAt");
        return date == null ? null : date.toInstant();
    }

    public Optional<UserRoster.Entry> find(String id) {
        var pipeline = projected(new Document("_id", ObjectId.isValid(id) ? new ObjectId(id) : id));
        var row = mongo.execute("users", collection -> collection.aggregate(pipeline).first());
        return Optional.ofNullable(row).map(this::entry);
    }

    private UserRoster.Entry entry(Document row) {
        return new UserRoster.Entry(row.get("_id").toString(), row.getString("name"), row.getString("email"),
                UserRole.valueOf(row.getString("role")), ManagementStatus.valueOf(row.getString("status")),
                createdAt(row), ((Number) row.getOrDefault("revision", 0)).longValue());
    }

    public boolean update(UserRoster.Entry before, ManagementAction action, UserRole role, Instant now) {
        var criteria = Criteria.where("_id").is(before.id()).and("deleted_at").is(null);
        criteria = before.role() == UserRole.USER ? criteria.and("role").in(Arrays.asList(null,"USER"))
                : criteria.and("role").is(before.role());
        criteria = before.status() == ManagementStatus.DISABLED ? criteria.and("locked").is(true) : criteria.and("locked").ne(true);
        criteria = before.revision() == 0 ? criteria.and("admin_revision").in(Arrays.asList(null,0L))
                : criteria.and("admin_revision").is(before.revision());
        var update = new Update().inc("admin_revision", 1).set("updated_at", now);
        switch (action) {
            case DELETE -> update.set("deleted_at", now).inc("session_epoch", 1);
            case DISABLE -> update.set("locked", true).inc("session_epoch", 1);
            case ENABLE -> update.set("locked", false);
            case CHANGE_ROLE -> update.set("role", role);
            case EDIT_NAME -> { /* The profile write joins the same transaction. */ }
            default -> throw new IllegalArgumentException("Invalid update");
        }
        return mongo.updateFirst(Query.query(criteria), update, User.class).getModifiedCount() == 1;
    }

    public boolean rename(String id, String expectedName, String name, Instant now) {
        var query = Query.query(Criteria.where("_id").is(id).and("display_name").is(expectedName));
        var update = new Update().set("display_name", name).set("updated_at", now);
        if (expectedName != null) return mongo.updateFirst(query, update, UserProfile.class).getMatchedCount() == 1;
        // A concurrent F01 profile edit must conflict with this write, including an insert for a missing legacy profile.
        var result = mongo.upsert(query, update.setOnInsert("cefr_level", CefrLevel.A1).setOnInsert("created_at", now), UserProfile.class);
        return result.getMatchedCount() == 1 || result.getUpsertedId() != null;
    }

    public void append(UserManagementAudit audit) { mongo.insert(audit); }
}
