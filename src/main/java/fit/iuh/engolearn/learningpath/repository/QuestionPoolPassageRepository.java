package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolPassage;
import fit.iuh.engolearn.learningpath.model.vo.PassagePublicationReviewVo;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Update;
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

    public boolean fenceQuestionReference(String passageId, int revision, String skill, String level,
            String actor, Instant updatedAt) {
        return fenceReference(passageId, revision, skill, level, actor, updatedAt, List.of("DRAFT", "ACTIVE", "RETIRED"));
    }

    public boolean fenceQuestionPublication(String passageId, int revision, String skill, String level,
            String actor, Instant updatedAt) {
        return fenceReference(passageId, revision, skill, level, actor, updatedAt, List.of("ACTIVE"));
    }

    private boolean fenceReference(String passageId, int revision, String skill, String level,
            String actor, Instant updatedAt, List<String> statuses) {
        Query query = Query.query(Criteria.where("passage_id").is(passageId).and("revision").is(revision)
                .and("skill").is(skill).and("level").is(level).and("status").in(statuses));
        return mongo.updateFirst(query, new Update().inc("revision", 1).set("updated_at", updatedAt)
                .set("updated_by", actor), QuestionPoolPassage.class).getModifiedCount() == 1;
    }

    public Optional<QuestionPoolPassageReadinessSource> findReadinessSource(String passageId) {
        Document source = mongo.findOne(Query.query(Criteria.where("passage_id").is(passageId)),
                Document.class, "question_pool_passages");
        if (source == null) return Optional.empty();
        // Read raw classifications so malformed imported drafts produce diagnostics, not enum conversion failures.
        List<String> malformed = new ArrayList<>();
        for (String field : List.of("skill", "level", "status", "title", "text", "transcript", "audio_media_id")) {
            if (source.get(field) != null && !(source.get(field) instanceof String)) malformed.add(field);
        }
        Integer revision = null;
        if (source.get("revision") instanceof Number number && number.doubleValue() == number.intValue()) {
            revision = number.intValue();
        } else if (source.get("revision") != null) malformed.add("revision");
        Object audio = source.get("audio");
        Document legacy = audio instanceof Document document ? document : null;
        if (audio != null && legacy == null) malformed.add("audio");
        if (legacy != null && legacy.get("url") != null && !(legacy.get("url") instanceof String)) malformed.add("audio.url");
        return Optional.of(new QuestionPoolPassageReadinessSource(
                passageId, stringField(source, "skill"), stringField(source, "level"), stringField(source, "status"),
                revision, stringField(source, "title"), stringField(source, "text"), stringField(source, "transcript"),
                stringField(source, "audio_media_id"), audio != null,
                legacy == null ? null : stringField(legacy, "url"), malformed));
    }

    private static String stringField(Document document, String field) {
        return document.get(field) instanceof String value ? value : null;
    }

    public QuestionPoolPassageCounts countByStatus() {
        var rows = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.group("status").count().as("count")), "question_pool_passages", Document.class)
                .getMappedResults();
        long total = 0, draft = 0, active = 0, retired = 0;
        for (Document row : rows) {
            long count = ((Number) row.get("count")).longValue();
            total += count;
            Object status = row.get("_id");
            if ("DRAFT".equals(status)) draft = count;
            else if ("ACTIVE".equals(status)) active = count;
            else if ("RETIRED".equals(status)) retired = count;
        }
        return new QuestionPoolPassageCounts(total, draft, active, retired);
    }

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

    public Optional<QuestionPoolPassage> findByPassageId(String passageId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("passage_id").is(passageId)),
                QuestionPoolPassage.class));
    }

    public Optional<QuestionPoolPassage> updateDraft(QuestionPoolPassage passage, int expectedRevision,
            boolean replaceAudio) {
        Query query = Query.query(Criteria.where("passage_id").is(passage.getPassageId())
                .and("status").is(QuestionPoolContentStatus.DRAFT).and("revision").is(expectedRevision));
        Update update = new Update().set("title", passage.getTitle()).set("skill", passage.getSkill())
                .set("level", passage.getLevel()).set("updated_at", passage.getUpdatedAt())
                .set("updated_by", passage.getUpdatedBy()).inc("revision", 1);
        if (passage.getText() == null) update.unset("text");
        else update.set("text", passage.getText());
        if (passage.getTranscript() == null) update.unset("transcript");
        else update.set("transcript", passage.getTranscript());
        if (replaceAudio) {
            update.unset("audio");
            if (passage.getAudioMediaId() == null) update.unset("audio_media_id");
            else update.set("audio_media_id", passage.getAudioMediaId());
        }
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), QuestionPoolPassage.class));
    }

    public boolean deleteDraft(String passageId, int expectedRevision) {
        Query query = Query.query(Criteria.where("passage_id").is(passageId)
                .and("status").is(QuestionPoolContentStatus.DRAFT).and("revision").is(expectedRevision));
        return mongo.findAndRemove(query, QuestionPoolPassage.class) != null;
    }

    public Optional<QuestionPoolPassage> publishDraft(String passageId, int expectedRevision, PassagePublicationReviewVo review) {
        Query query = Query.query(Criteria.where("passage_id").is(passageId)
                .and("status").is(QuestionPoolContentStatus.DRAFT).and("revision").is(expectedRevision));
        Update update = new Update().set("status", QuestionPoolContentStatus.ACTIVE).inc("revision", 1)
                .set("updated_at", review.getReviewedAt()).set("updated_by", review.getReviewedBy())
                .set("publication_review", review);
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), QuestionPoolPassage.class));
    }

    public QuestionPoolPassage insert(QuestionPoolPassage passage) {
        return mongo.insert(passage);
    }

    public Optional<QuestionPoolPassage> retireActive(String passageId, int expectedRevision, String actor, Instant retiredAt) {
        Query query = Query.query(Criteria.where("passage_id").is(passageId)
                .and("status").is(QuestionPoolContentStatus.ACTIVE).and("revision").is(expectedRevision));
        Update update = new Update().set("status", QuestionPoolContentStatus.RETIRED).inc("revision", 1)
                .set("updated_at", retiredAt).set("updated_by", actor);
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), QuestionPoolPassage.class));
    }
}
