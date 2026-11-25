package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolPassage;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public QuestionPoolPassage insert(QuestionPoolPassage passage) {
        return mongo.insert(passage);
    }
}
