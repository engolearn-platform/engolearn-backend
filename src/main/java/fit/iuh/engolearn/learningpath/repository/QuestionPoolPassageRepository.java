package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolPassage;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QuestionPoolPassageRepository {
    private final MongoTemplate mongo;

    public Page<QuestionPoolPassage> search(String keyword, QuestionPoolSkill skill, CefrLevel level,
            QuestionPoolContentStatus status, int page, int size) {
        List<Criteria> filters = new ArrayList<>();
        if (skill != null) filters.add(Criteria.where("skill").is(skill));
        if (level != null) filters.add(Criteria.where("level").is(level));
        if (status != null) filters.add(Criteria.where("status").is(status));
        if (keyword != null && !keyword.isBlank()) {
            String literal = Pattern.quote(keyword.strip());
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(literal, "i"),
                    Criteria.where("text").regex(literal, "i"),
                    Criteria.where("transcript").regex(literal, "i")));
        }
        Criteria criteria = filters.isEmpty() ? new Criteria()
                : new Criteria().andOperator(filters.toArray(Criteria[]::new));
        long total = mongo.count(Query.query(criteria), QuestionPoolPassage.class);
        PageRequest pagination = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updated_at", "_id"));
        if (pagination.getOffset() >= total) {
            return new PageImpl<>(List.of(), pagination, total);
        }
        Query query = Query.query(criteria).with(pagination);
        query.fields().include("passage_id", "title", "skill", "level", "status", "revision",
                "created_at", "updated_at", "audio_media_id", "audio.url");
        return new PageImpl<>(mongo.find(query, QuestionPoolPassage.class), pagination, total);
    }

    public QuestionPoolPassage insert(QuestionPoolPassage passage) {
        return mongo.insert(passage);
    }
}
