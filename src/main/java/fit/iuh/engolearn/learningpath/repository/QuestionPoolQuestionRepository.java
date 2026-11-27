package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolQuestion;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPublicationReviewVo;
import org.bson.Document;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QuestionPoolQuestionRepository {
    private final MongoTemplate mongo;

    public Optional<QuestionPoolQuestionRetirementSource> retireActive(String questionId, int revision, String actor, java.time.Instant retiredAt) {
        var saved = mongo.findAndModify(Query.query(Criteria.where("question_id").is(questionId)
                        .and("status").is("ACTIVE").and("revision").is(revision)),
                new Update().set("status", "RETIRED").inc("revision", 1).set("updated_by", actor).set("updated_at", retiredAt),
                FindAndModifyOptions.options().returnNew(true), Document.class, "question_pool");
        return saved == null ? Optional.empty() : Optional.of(new QuestionPoolQuestionRetirementSource(
                questionId, ((Number) saved.get("revision")).intValue(), actor, retiredAt));
    }

    public Optional<QuestionPoolQuestionReadinessSource> findReadinessSource(String questionId) {
        Document source = mongo.findOne(Query.query(Criteria.where("question_id").is(questionId)), Document.class, "question_pool");
        if (source == null) return Optional.empty();
        List<String> malformed = new ArrayList<>();
        for (String field : List.of("question_id", "status", "skill", "micro_skill", "level", "passage_id", "prompt", "correct_option_id", "explanation")) {
            if (source.get(field) != null && !(source.get(field) instanceof String)) malformed.add(field);
        }
        Integer revision = null;
        if (source.get("revision") instanceof Number number && number.doubleValue() == number.intValue()) revision = number.intValue();
        else if (source.get("revision") != null) malformed.add("revision");
        List<QuestionPoolQuestionOptionReadinessSource> options = null;
        if (source.get("options") instanceof List<?> values) {
            options = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) instanceof Document option) {
                    for (String field : List.of("option_id", "text")) {
                        if (option.get(field) != null && !(option.get(field) instanceof String)) malformed.add("options[" + i + "]." + field);
                    }
                    options.add(new QuestionPoolQuestionOptionReadinessSource(stringField(option, "option_id"), stringField(option, "text")));
                } else {
                    malformed.add("options[" + i + "]");
                    options.add(new QuestionPoolQuestionOptionReadinessSource(null, null));
                }
            }
        } else if (source.get("options") != null) malformed.add("options");
        return Optional.of(new QuestionPoolQuestionReadinessSource(questionId, revision, stringField(source, "status"),
                stringField(source, "skill"), stringField(source, "micro_skill"), stringField(source, "level"),
                stringField(source, "passage_id"), stringField(source, "prompt"), options,
                stringField(source, "correct_option_id"), stringField(source, "explanation"), malformed));
    }

    private String stringField(Document source, String field) {
        return source.get(field) instanceof String value ? value : null;
    }

    public Optional<QuestionPoolQuestion> publishDraft(String questionId, int revision, QuestionPublicationReviewVo review) {
        Query query = Query.query(Criteria.where("question_id").is(questionId).and("status").is(QuestionPoolContentStatus.DRAFT)
                .and("revision").is(revision));
        Update update = new Update().set("status", QuestionPoolContentStatus.ACTIVE).inc("revision", 1)
                .set("updated_at", review.getReviewedAt()).set("updated_by", review.getReviewedBy()).set("publication_review", review);
        return Optional.ofNullable(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), QuestionPoolQuestion.class));
    }

    public QuestionPoolQuestion insert(QuestionPoolQuestion question) {
        return mongo.insert(question);
    }

    public Optional<QuestionPoolQuestion> updateDraft(QuestionPoolQuestion question, int revision) {
        Query query = Query.query(Criteria.where("question_id").is(question.getQuestionId())
                .and("status").is(QuestionPoolContentStatus.DRAFT).and("revision").is(revision));
        Update update = new Update().set("skill", question.getSkill()).set("micro_skill", question.getMicroSkill())
                .set("level", question.getLevel()).set("prompt", question.getPrompt()).set("options", question.getOptions())
                .set("correct_option_id", question.getCorrectOptionId()).set("explanation", question.getExplanation())
                .set("updated_at", question.getUpdatedAt()).set("updated_by", question.getUpdatedBy()).inc("revision", 1);
        if (question.getPassageId() == null) update.unset("passage_id");
        else update.set("passage_id", question.getPassageId());
        return Optional.ofNullable(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), QuestionPoolQuestion.class));
    }

    public boolean deleteDraft(String questionId, int revision) {
        return mongo.findAndRemove(Query.query(Criteria.where("question_id").is(questionId)
                .and("status").is(QuestionPoolContentStatus.DRAFT).and("revision").is(revision)), QuestionPoolQuestion.class) != null;
    }

    public Optional<QuestionPoolQuestion> findByQuestionId(String questionId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("question_id").is(questionId)),
                QuestionPoolQuestion.class));
    }

    public Page<QuestionPoolQuestion> search(String keyword, QuestionPoolSkill skill, String microSkill,
            CefrLevel level, QuestionPoolContentStatus status, String passageId, int page, int size) {
        List<Criteria> filters = new ArrayList<>();
        if (skill != null) filters.add(Criteria.where("skill").is(skill));
        if (microSkill != null && !microSkill.isBlank()) filters.add(Criteria.where("micro_skill").is(microSkill.strip()));
        if (level != null) filters.add(Criteria.where("level").is(level));
        if (status != null) filters.add(Criteria.where("status").is(status));
        if (passageId != null && !passageId.isBlank()) filters.add(Criteria.where("passage_id").is(passageId.strip()));
        if (keyword != null && !keyword.isBlank()) {
            String literal = Pattern.quote(keyword.strip());
            filters.add(new Criteria().orOperator(Criteria.where("question_id").regex(literal, "i"),
                    Criteria.where("prompt").regex(literal, "i")));
        }
        Criteria criteria = filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters.toArray(Criteria[]::new));
        long total = mongo.count(Query.query(criteria), QuestionPoolQuestion.class);
        var pagination = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updated_at", "_id"));
        if (pagination.getOffset() >= total) {
            return new PageImpl<>(List.of(), pagination, total);
        }
        Query query = Query.query(criteria).with(pagination);
        query.fields().include("question_id", "skill", "micro_skill", "level", "passage_id", "prompt",
                "status", "revision", "created_at", "updated_at");
        return new PageImpl<>(mongo.find(query, QuestionPoolQuestion.class), pagination, total);
    }
}
