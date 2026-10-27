package fit.iuh.engolearn.config;

import fit.iuh.engolearn.config.MongoCollectionInspector.Issue;
import fit.iuh.engolearn.config.MongoCollectionInspector.Kind;
import fit.iuh.engolearn.config.MongoCollectionInspector.Report;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

// The shared test classpath is scanned by the Spring context, so the unit tests' @Document
// fixtures are mapped here too: assertions must stay independent of entity/collection counts.
@EnabledIfDockerAvailable
class MongoCollectionBootstrapIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private MongoMappingContext mappingContext;

    @Test
    void recreatesAMissingCollectionWithoutFailing() {
        newMongoInspector().inspectAndCreateMissing();
        mongoTemplate.dropCollection("topics");
        assertThat(mongoTemplate.collectionExists("topics")).isFalse();

        Report report = newMongoInspector().inspectAndCreateMissing();

        assertThat(report.skipped()).isFalse();
        assertThat(mongoTemplate.collectionExists("topics")).isTrue();
        assertThat(report.collectionsCreated()).contains("topics");
    }

    @Test
    void reportsATypeMismatchFoundInASampleDocument() {
        newMongoInspector().inspectAndCreateMissing();
        mongoTemplate.getCollection("user_topic_progress")
                .insertOne(new Document("user_id", 42).append("topic_id", "t1"));

        Report report = newMongoInspector().inspectAndCreateMissing();

        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.kind()).isEqualTo(Kind.TYPE_MISMATCH);
                    assertThat(issue.collection()).isEqualTo("user_topic_progress");
                    assertThat(issue.field()).isEqualTo("user_id");
                    assertThat(issue.detail()).isEqualTo("expected String but found Int32");
                });
        assertThat(report.issues())
                .noneSatisfy(issue -> {
                    assertThat(issue.kind()).isEqualTo(Kind.TYPE_MISMATCH);
                    assertThat(issue.field()).isEqualTo("topic_id");
                });
    }

    @Test
    void aSecondRunCreatesNothingAndStaysQuiet() {
        newMongoInspector().inspectAndCreateMissing();

        Report second = newMongoInspector().inspectAndCreateMissing();

        assertThat(second.skipped()).isFalse();
        assertThat(second.collectionsCreated()).isEmpty();
        assertThat(second.issues())
                .extracting(Issue::kind)
                .containsOnly(Kind.DUPLICATE_MAPPING);
    }

    private MongoCollectionInspector newMongoInspector() {
        return new MongoCollectionInspector(mongoTemplate, mappingContext);
    }
}
