package fit.iuh.engolearn.microskill.repository;

import fit.iuh.engolearn.microskill.model.MicroSkill;
import fit.iuh.engolearn.microskill.model.enums.MicroSkillStatus;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;

@Repository
@RequiredArgsConstructor
public class MicroSkillRepository {
    private final MongoTemplate mongo;
    public List<MicroSkill> list() {
        return mongo.find(new Query().with(Sort.by("display_order", "code")), MicroSkill.class);
    }
    public Optional<MicroSkill> findById(String id) { return Optional.ofNullable(mongo.findById(id, MicroSkill.class)); }
    public Optional<MicroSkill> findByCanonicalName(String name) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("canonical_name").is(name)), MicroSkill.class));
    }
    public MicroSkill insert(MicroSkill value) { return mongo.insert(value); }
    public Optional<MicroSkill> update(MicroSkill value, int revision) {
        var update = new Update().set("display_name_vi", value.getDisplayNameVi()).set("description_vi", value.getDescriptionVi())
                .set("display_order", value.getDisplayOrder()).set("status", value.getStatus())
                .set("updated_by", value.getUpdatedBy()).set("updated_at", Instant.now()).inc("revision", 1);
        return Optional.ofNullable(mongo.findAndModify(Query.query(Criteria.where("_id").is(value.getId()).and("revision").is(revision)),
                update, FindAndModifyOptions.options().returnNew(true), MicroSkill.class));
    }
    public Optional<MicroSkill> retainActive(String name) {
        // Competes with deletion on the same document, including consumers participating in a Mongo transaction.
        return Optional.ofNullable(mongo.findAndModify(Query.query(Criteria.where("canonical_name").is(name)
                .and("status").is(MicroSkillStatus.ACTIVE)), new Update().set("referenced", true),
                FindAndModifyOptions.options().returnNew(true), MicroSkill.class));
    }
    public boolean deleteUnused(String id, int revision) {
        return mongo.remove(Query.query(Criteria.where("_id").is(id).and("revision").is(revision)
                .and("referenced").is(false)), MicroSkill.class).getDeletedCount() == 1;
    }
}
