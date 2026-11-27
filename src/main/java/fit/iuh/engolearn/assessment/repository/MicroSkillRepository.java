package fit.iuh.engolearn.assessment.repository;

import fit.iuh.engolearn.assessment.model.MicroSkill;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@RequiredArgsConstructor
public class MicroSkillRepository {
    private final MongoTemplate mongo;

    public List<MicroSkill> list() {
        return mongo.find(new Query().with(Sort.by("_id")), MicroSkill.class);
    }

    public MicroSkill find(String code) { return mongo.findById(code, MicroSkill.class); }

    public MicroSkill insert(MicroSkill skill) { return mongo.insert(skill); }

    public MicroSkill update(String code, Update update) {
        return mongo.findAndModify(Query.query(where("_id").is(code)), update.currentDate("updated_at"),
                FindAndModifyOptions.options().returnNew(true), MicroSkill.class);
    }
}
