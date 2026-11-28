package fit.iuh.engolearn.microskill;

import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.file.*;
import java.util.*;

public final class MicroSkillFixtures {
    private MicroSkillFixtures() {}
    public static List<Document> documents() {
        try {
            return Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/micro-skills.json")))
                    .get("collections", Document.class).getList("micro_skills", Document.class);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    public static void seed(MongoTemplate mongo) { mongo.getCollection("micro_skills").insertMany(documents()); }
    public static MicroSkillLookup lookup() {
        var values = documents().stream().map(value -> new MicroSkillDto(value.getString("_id"), value.getString("code"),
                value.getString("canonical_name"), value.getString("display_name_vi"), value.getString("description_vi"),
                value.getString("skill"), "ACTIVE".equals(value.getString("status")), value.getInteger("display_order"))).toList();
        return new MicroSkillLookup() {
            public List<MicroSkillDto> list() { return values; }
            public Optional<MicroSkillDto> findById(String id) { return values.stream().filter(value -> value.id().equals(id)).findFirst(); }
            public Optional<MicroSkillDto> findByCanonicalName(String name) { return values.stream().filter(value -> value.canonicalName().equals(name)).findFirst(); }
            public Optional<MicroSkillDto> retainActive(String name) { return findByCanonicalName(name).filter(MicroSkillDto::active); }
        };
    }
}
