package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
}
