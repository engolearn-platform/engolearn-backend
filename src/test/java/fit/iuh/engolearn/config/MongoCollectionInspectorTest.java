package fit.iuh.engolearn.config;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import fit.iuh.engolearn.config.MongoCollectionInspector.Issue;
import fit.iuh.engolearn.config.MongoCollectionInspector.Kind;
import fit.iuh.engolearn.config.MongoCollectionInspector.Report;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoCollectionInspectorTest {

    @Test
    void twoEntitiesOnOneCollectionAreReportedAsDuplicateMapping() {
        MongoTemplate template = templateWithCollections("shared_collection");

        Report report = new MongoCollectionInspector(template,
                contextWith(DuplicateAlpha.class, DuplicateBeta.class)).inspectAndCreateMissing();

        assertThat(report.skipped()).isFalse();
        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.kind()).isEqualTo(Kind.DUPLICATE_MAPPING);
                    assertThat(issue.collection()).isEqualTo("shared_collection");
                    assertThat(issue.detail())
                            .contains(DuplicateAlpha.class.getName(), DuplicateBeta.class.getName());
                });
    }

    @Test
    void sampleDocumentTypesAreCheckedAgainstTheCompatibilityTable() {
        MongoTemplate template = templateWithCollections();
        org.bson.Document sample = new org.bson.Document("_id", "doc-1")
                .append("text", 42)
                .append("oid_text", new ObjectId())
                .append("created_at", new Date())
                .append("local_created_at", new Date())
                .append("local_date", new Date())
                .append("count", 5_000_000_000L)
                .append("big_count", 7)
                .append("ratio", new Decimal128(BigDecimal.TEN))
                .append("done", true)
                .append("status", "ACTIVE")
                .append("tags", List.of("a"))
                .append("attributes", new org.bson.Document("k", "v"))
                .append("nested", new org.bson.Document("label", "ok"));
        when(template.findOne(any(Query.class), eq(org.bson.Document.class), eq("type_checks")))
                .thenReturn(sample);

        Report report = new MongoCollectionInspector(template, contextWith(TypeCheckDocument.class))
                .inspectAndCreateMissing();

        assertThat(report.issues())
                .filteredOn(issue -> issue.kind() == Kind.TYPE_MISMATCH)
                .extracting(Issue::field)
                .containsExactlyInAnyOrder("text", "count");
        assertThat(report.collectionsCreated()).containsExactly("type_checks");
    }

    @Test
    void uncertainAndUnmappedValuesStaySilent() {
        MongoTemplate template = templateWithCollections("type_checks");
        org.bson.Document sample = new org.bson.Document("_id", "doc-2")
                .append("text", new ObjectId())
                .append("status", 1)
                .append("created_at", "not a date")
                .append("attributes", List.of("x"))
                .append("nested", new org.bson.Document("label", 42))
                .append("local_date", 123)
                .append("unmapped_path", List.of(1))
                .append("done", null);
        when(template.findOne(any(Query.class), eq(org.bson.Document.class), eq("type_checks")))
                .thenReturn(sample);

        Report report = new MongoCollectionInspector(template, contextWith(TypeCheckDocument.class))
                .inspectAndCreateMissing();

        assertThat(report.issues())
                .filteredOn(issue -> issue.kind() == Kind.TYPE_MISMATCH)
                .extracting(Issue::field)
                .containsExactlyInAnyOrder("text", "status", "created_at", "attributes", "nested.label");
        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.field()).isEqualTo("text");
                    assertThat(issue.detail()).isEqualTo("expected String but found ObjectId");
                });
    }

    @Test
    void unreachableMongoIsReportedAsSkippedInsteadOfThrown() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.getCollectionNames()).thenThrow(new MongoTimeoutException("connect timed out"));

        Report report = new MongoCollectionInspector(template, contextWith(DuplicateAlpha.class))
                .inspectAndCreateMissing();

        assertThat(report.skipped()).isTrue();
        assertThat(report.skipReason()).contains("connect timed out");
        assertThat(report.issues()).isEmpty();
        assertThat(report.collectionsCreated()).isEmpty();
    }

    @Test
    void aFailedCreateIsReportedAndDoesNotStopTheOtherCollections() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.getCollectionNames()).thenReturn(Set.of());
        doThrow(new MongoException(13, "not authorized"))
                .when(template).createCollection("create_fail");

        Report report = new MongoCollectionInspector(template,
                contextWith(CreateFailDoc.class, CreateOkDoc.class)).inspectAndCreateMissing();

        assertThat(report.collectionsCreated()).containsExactlyInAnyOrder("create_ok");
        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.kind()).isEqualTo(Kind.CREATE_FAILED);
                    assertThat(issue.collection()).isEqualTo("create_fail");
                    assertThat(issue.detail()).isEqualTo("not authorized");
                });
    }

    @Test
    void aCreateRaceIsTreatedAsSuccess() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.getCollectionNames()).thenReturn(Set.of());
        doThrow(new MongoException(48, "Collection already exists."))
                .when(template).createCollection("race_collection");

        Report report = new MongoCollectionInspector(template, contextWith(RaceDoc.class))
                .inspectAndCreateMissing();

        assertThat(report.skipped()).isFalse();
        assertThat(report.collectionsCreated()).isEmpty();
        assertThat(report.issues()).noneMatch(issue -> issue.kind() == Kind.CREATE_FAILED);
    }

    @Test
    void vetoSuppressesCrossEntityTypeDisagreement() {
        MongoTemplate template = templateWithCollections("veto_collection");
        when(template.findOne(any(Query.class), eq(org.bson.Document.class), eq("veto_collection")))
                .thenReturn(new org.bson.Document("x", new ObjectId()));

        Report report = new MongoCollectionInspector(template,
                contextWith(VetoStringDoc.class, VetoObjectIdDoc.class)).inspectAndCreateMissing();

        assertThat(report.issues())
                .filteredOn(issue -> issue.kind() == Kind.TYPE_MISMATCH)
                .extracting(Issue::field)
                .doesNotContain("x");
    }

    @Test
    void vetoDoesNotHideAGenuineMismatch() {
        MongoTemplate template = templateWithCollections("genuine_mismatch");
        when(template.findOne(any(Query.class), eq(org.bson.Document.class), eq("genuine_mismatch")))
                .thenReturn(new org.bson.Document("x", 42));

        Report report = new MongoCollectionInspector(template,
                contextWith(GenuineMismatchAlpha.class, GenuineMismatchBeta.class)).inspectAndCreateMissing();

        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.kind()).isEqualTo(Kind.TYPE_MISMATCH);
                    assertThat(issue.field()).isEqualTo("x");
                });
    }

    @Test
    void anObjectIdIdIsAcceptedForAStringIdProperty() {
        MongoTemplate template = templateWithCollections("type_checks");
        when(template.findOne(any(Query.class), eq(org.bson.Document.class), eq("type_checks")))
                .thenReturn(new org.bson.Document("_id", new ObjectId()));

        Report report = new MongoCollectionInspector(template, contextWith(TypeCheckDocument.class))
                .inspectAndCreateMissing();

        assertThat(report.issues())
                .filteredOn(issue -> issue.kind() == Kind.TYPE_MISMATCH)
                .isEmpty();
    }

    // Mirrors Boot's DataMongoConfiguration: without the Mongo conversions' simple type
    // holder the context tries to create entities for java.time fields and fails on JDK 21.
    private static MongoMappingContext contextWith(Class<?>... types) {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(types));
        context.afterPropertiesSet();
        return context;
    }

    private static MongoTemplate templateWithCollections(String... names) {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.getCollectionNames()).thenReturn(Set.of(names));
        return template;
    }

    @org.springframework.data.mongodb.core.mapping.Document("shared_collection")
    static class DuplicateAlpha {
        @Id
        String id;
        String name;
    }

    @org.springframework.data.mongodb.core.mapping.Document("shared_collection")
    static class DuplicateBeta {
        @Id
        String id;
        String name;
    }

    @org.springframework.data.mongodb.core.mapping.Document("type_checks")
    static class TypeCheckDocument {
        @Id
        String id;
        String text;
        @Field(value = "oid_text", targetType = FieldType.OBJECT_ID)
        String oidText;
        @Field("created_at")
        Instant createdAt;
        @Field("local_created_at")
        LocalDateTime localCreatedAt;
        @Field("local_date")
        LocalDate localDate;
        Integer count;
        Long bigCount;
        Double ratio;
        Boolean done;
        Status status;
        List<String> tags;
        Map<String, String> attributes;
        Nested nested;
    }

    enum Status {
        ACTIVE, ARCHIVED
    }

    static class Nested {
        String label;
        Integer amount;
    }

    @org.springframework.data.mongodb.core.mapping.Document("create_fail")
    static class CreateFailDoc {
        @Id
        String id;
    }

    @org.springframework.data.mongodb.core.mapping.Document("create_ok")
    static class CreateOkDoc {
        @Id
        String id;
    }

    @org.springframework.data.mongodb.core.mapping.Document("race_collection")
    static class RaceDoc {
        @Id
        String id;
    }

    @org.springframework.data.mongodb.core.mapping.Document("veto_collection")
    static class VetoStringDoc {
        @Id
        String id;
        String x;
    }

    @org.springframework.data.mongodb.core.mapping.Document("veto_collection")
    static class VetoObjectIdDoc {
        @Id
        String id;
        @Field(value = "x", targetType = FieldType.OBJECT_ID)
        String x;
    }

    @org.springframework.data.mongodb.core.mapping.Document("genuine_mismatch")
    static class GenuineMismatchAlpha {
        @Id
        String id;
        String x;
    }

    @org.springframework.data.mongodb.core.mapping.Document("genuine_mismatch")
    static class GenuineMismatchBeta {
        @Id
        String id;
        String x;
    }
}
