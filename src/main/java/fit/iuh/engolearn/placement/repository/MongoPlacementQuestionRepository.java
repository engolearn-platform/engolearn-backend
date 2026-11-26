package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class MongoPlacementQuestionRepository implements PlacementQuestionRepository {
    private final MongoTemplate mongo;

    @Override
    public Optional<PlacementQuestion> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PlacementQuestion.class));
    }

    @Override
    public List<PlacementQuestion> findAllById(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)).with(Sort.by(Sort.Direction.ASC, "_id")),
                PlacementQuestion.class);
    }

    @Override
    public List<PlacementQuestion> findActiveBySkillAndLevel(PlacementSkill skill, PlacementLevel level) {
        return mongo.find(Query.query(Criteria.where("status").is(PlacementQuestionStatus.ACTIVE)
                        .and("skill").is(skill).and("level").is(level))
                        .with(Sort.by(Sort.Direction.ASC, "_id")), PlacementQuestion.class);
    }

    @Override
    public List<PlacementQuestion> findActiveByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)
                        .and("status").is(PlacementQuestionStatus.ACTIVE))
                        .with(Sort.by(Sort.Direction.ASC, "_id")), PlacementQuestion.class);
    }

    @Override
    public AdminPage findAdminPage(String search, PlacementSkill skill, PlacementLevel level,
                                   PlacementQuestionStatus status, int page, int size) {
        Query filters = new Query();
        if (!search.isEmpty()) {
            Pattern literal = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            filters.addCriteria(new Criteria().orOperator(Criteria.where("code").regex(literal),
                    Criteria.where("prompt").regex(literal), Criteria.where("passage").regex(literal)));
        }
        if (skill != null) filters.addCriteria(Criteria.where("skill").is(skill));
        if (level != null) filters.addCriteria(Criteria.where("level").is(level));
        if (status != null) filters.addCriteria(Criteria.where("status").is(status));
        long total = mongo.count(filters, PlacementQuestion.class);
        Query query = filters.with(Sort.by("code", "_id")).skip((long) page * size).limit(size);
        return new AdminPage(mongo.find(query, PlacementQuestion.class), total);
    }

    @Override
    public Map<String, String> findListeningAudioTitles() {
        Query query = Query.query(Criteria.where("skill").is(PlacementSkill.LISTENING)
                .and("provenance.audio_sha256").exists(true)).with(Sort.by("code", "_id"));
        query.fields().include("provenance.audio_sha256").include("provenance.title");
        Map<String, String> titles = new HashMap<>();
        for (PlacementQuestion question : mongo.find(query, PlacementQuestion.class)) {
            var provenance = question.getProvenance();
            if (provenance != null && provenance.getAudioSha256() != null
                    && provenance.getTitle() != null && !provenance.getTitle().isBlank()) {
                titles.putIfAbsent(provenance.getAudioSha256(), provenance.getTitle());
            }
        }
        return titles;
    }

    @Override
    public PlacementQuestion insert(PlacementQuestion question) {
        return mongo.insert(question);
    }

    @Override
    public void insertAll(List<PlacementQuestion> questions) {
        mongo.insert(questions, PlacementQuestion.class);
    }

    @Override
    public Optional<PlacementQuestion> updateIfCurrent(PlacementQuestion question, long revision) {
        Update update = new Update()
                .set("code", question.getCode())
                .set("level", question.getLevel())
                .set("prompt", question.getPrompt())
                .set("passage", question.getPassage())
                .set("options", question.getOptions())
                .set("correct_option_id", question.getCorrectOptionId())
                .set("explanation", question.getExplanation())
                .set("transcript", question.getTranscript())
                .set("audio_media_id", question.getAudioMediaId())
                .set("audio_url", question.getAudioUrl())
                .set("audio_duration_seconds", question.getAudioDurationSeconds())
                .set("admin_audio_override", question.isAdminAudioOverride());
        return updateIfCurrent(question.getId(), revision, update);
    }

    @Override
    public Optional<PlacementQuestion> updateStatusIfCurrent(PlacementQuestion question, long revision) {
        Update update = new Update().set("status", question.getStatus());
        if (question.getStatus() == PlacementQuestionStatus.ACTIVE && question.getSkill() == PlacementSkill.LISTENING) {
            update.set("audio_media_id", question.getAudioMediaId())
                .set("audio_url", question.getAudioUrl());
        }
        return updateIfCurrent(question.getId(), revision, update);
    }

    private Optional<PlacementQuestion> updateIfCurrent(String id, long revision, Update update) {
        Criteria criteria = Criteria.where("_id").is(id);
        if (revision == 0) {
            criteria.orOperator(Criteria.where("revision").is(0L), Criteria.where("revision").exists(false));
        } else {
            criteria.and("revision").is(revision);
        }
        update.set("updated_at", Instant.now()).inc("revision", 1L);
        return Optional.ofNullable(mongo.findAndModify(Query.query(criteria), update,
                FindAndModifyOptions.options().returnNew(true), PlacementQuestion.class));
    }
}
