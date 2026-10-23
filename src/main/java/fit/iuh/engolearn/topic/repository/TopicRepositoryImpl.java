package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MongoTemplate-backed custom implementation of {@link TopicSearchCustom}
 * (design record F05 §3.6). Registered as {@code TopicRepository}'s custom
 * implementation via the {@code <RepositoryInterface>Impl} naming rule — do
 * not rename to {@code TopicSearchCustomImpl}.
 *
 * <p>Search is a case-insensitive substring match on {@code titleEn} OR
 * {@code titleVi}, regex-escaped with {@link Pattern#quote} (no regex
 * injection). All filters AND-combine. Property names are used and mapped to
 * their {@code @Field} names by the query mapper.</p>
 */
public class TopicRepositoryImpl implements TopicSearchCustom {

    private final MongoTemplate mongoTemplate;

    public TopicRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Topic> search(TopicManagementSearchCriteria criteria, Pageable pageable) {
        Criteria filter = buildFilter(criteria);
        List<Topic> content = mongoTemplate.find(new Query(filter).with(pageable), Topic.class);
        long total = mongoTemplate.count(new Query(filter), Topic.class);
        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }

    private Criteria buildFilter(TopicManagementSearchCriteria criteria) {
        List<Criteria> clauses = new ArrayList<>();
        String search = criteria.search();
        if (search != null && !search.isBlank()) {
            String quoted = Pattern.quote(search);
            clauses.add(new Criteria().orOperator(
                    Criteria.where("titleEn").regex(quoted, "i"),
                    Criteria.where("titleVi").regex(quoted, "i")));
        }
        if (criteria.cefr() != null) {
            clauses.add(Criteria.where("cefrLevel").is(criteria.cefr()));
        }
        if (criteria.category() != null) {
            clauses.add(Criteria.where("category").is(criteria.category()));
        }
        if (criteria.published() != null) {
            clauses.add(criteria.published()
                    ? Criteria.where("latestPublishedVersion").exists(true).ne(null)
                    : Criteria.where("latestPublishedVersion").is(null));
        }
        if (clauses.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().andOperator(clauses);
    }
}
