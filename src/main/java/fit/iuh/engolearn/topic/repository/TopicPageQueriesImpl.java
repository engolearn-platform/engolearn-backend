package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class TopicPageQueriesImpl implements TopicPageQueries {

    private static final String ID_FIELD = "_id";
    private static final String PUBLISHED_POINTER = "latest_published_version";
    private static final String LEVEL_FIELD = "cefr_level";
    private static final String TITLE_EN = "title_en";
    private static final String TITLE_VI = "title_vi";

    private final MongoTemplate mongoTemplate;

    TopicPageQueriesImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Topic> findPublishedPage(@Nullable CefrLevel cefrLevel,
                                         @Nullable String keyword,
                                         TopicRepository.SortKey sortKey,
                                         @Nullable Integer cursorSortKey,
                                         @Nullable String cursorId,
                                         int limit) {
        if (sortKey == null) {
            throw new IllegalArgumentException("sortKey is required");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (cursorId == null && cursorSortKey != null) {
            throw new IllegalArgumentException("cursorSortKey requires a cursor id");
        }

        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where(PUBLISHED_POINTER).ne(null));
        if (cefrLevel != null) {
            filters.add(Criteria.where(LEVEL_FIELD).is(cefrLevel));
        }
        if (keyword != null && !keyword.isBlank()) {
            filters.add(titleCriteria(keyword));
        }
        if (cursorId != null) {
            filters.add(afterCursorCriteria(sortKey, cursorSortKey, cursorId));
        }

        Query query = new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new)))
                .with(Sort.by(Sort.Direction.ASC, sortKey.field(), ID_FIELD))
                .limit(limit);
        return mongoTemplate.find(query, Topic.class);
    }

    @Override
    public List<Topic> findPublishedCandidates(@Nullable CefrLevel cefrLevel, @Nullable String keyword) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where(PUBLISHED_POINTER).ne(null));
        if (cefrLevel != null) filters.add(Criteria.where(LEVEL_FIELD).is(cefrLevel));
        if (keyword != null && !keyword.isBlank()) filters.add(titleCriteria(keyword));
        Query query = new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        query.fields().include(ID_FIELD).include(PUBLISHED_POINTER);
        return mongoTemplate.find(query, Topic.class);
    }

    private static Criteria titleCriteria(String keyword) {
        Pattern pattern = Pattern.compile(Pattern.quote(keyword.trim()),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new Criteria().orOperator(
                Criteria.where(TITLE_EN).regex(pattern),
                Criteria.where(TITLE_VI).regex(pattern));
    }

    private static Criteria afterCursorCriteria(TopicRepository.SortKey sortKey,
                                                @Nullable Integer cursorSortKey,
                                                String cursorId) {
        String field = sortKey.field();
        Criteria idAfterCursor = Criteria.where(ID_FIELD).gt(storedIdValue(cursorId));
        if (cursorSortKey == null) {
            // MongoDB orders a missing or null sort key before every present value, so only
            // other null-key rows can still follow a cursor row without a sort key.
            return new Criteria().orOperator(
                    new Criteria().andOperator(Criteria.where(field).is(null), idAfterCursor),
                    Criteria.where(field).ne(null));
        }
        return new Criteria().orOperator(
                Criteria.where(field).gt(cursorSortKey),
                new Criteria().andOperator(Criteria.where(field).is(cursorSortKey), idAfterCursor));
    }

    private static Object storedIdValue(String id) {
        return ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
