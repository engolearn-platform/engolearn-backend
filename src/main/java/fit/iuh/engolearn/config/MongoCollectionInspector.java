package fit.iuh.engolearn.config;

import com.mongodb.MongoException;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.MongoSimpleTypes;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates collections that mapped entities expect but the database lacks, and reports
 * duplicate mappings plus sample-document type mismatches. Create-only and best-effort:
 * existing data is never altered and no failure escapes {@link #inspectAndCreateMissing()}.
 */
public class MongoCollectionInspector {

    public enum Kind {
        DUPLICATE_MAPPING, COLLECTION_MISSING, CREATE_FAILED, TYPE_MISMATCH
    }

    public record Issue(Kind kind, String collection, String field, String detail) {
    }

    public record Report(List<String> collectionsCreated, List<Issue> issues,
                         boolean skipped, String skipReason) {
        public Report {
            collectionsCreated = List.copyOf(collectionsCreated);
            issues = List.copyOf(issues);
        }

        public static Report skipped(String reason) {
            return new Report(List.of(), List.of(), true, reason);
        }
    }

    private static final String CLASS_FIELD = "_class";
    private static final String ID_FIELD = "_id";
    // MongoDB server error code 48 (NamespaceExists). Spring Data 5 no longer maps it to a
    // dedicated exception, so the create race is recognised on the driver error code.
    private static final int NAMESPACE_EXISTS_ERROR_CODE = 48;

    private final MongoTemplate template;
    private final MongoMappingContext mappingContext;
    private List<String> unmappedCollections = List.of();

    public MongoCollectionInspector(MongoTemplate template, MongoMappingContext mappingContext) {
        this.template = template;
        this.mappingContext = mappingContext;
    }

    /** Database collections with no mapped entity, from the last inspection; caller decides how to log them. */
    public List<String> unmappedCollections() {
        return unmappedCollections;
    }

    public Report inspectAndCreateMissing() {
        List<Issue> issues = new ArrayList<>();
        Map<String, List<MongoPersistentEntity<?>>> byCollection = groupMappedEntities(issues);
        Map<Class<?>, MongoPersistentEntity<?>> entitiesByType = indexEntitiesByType();

        Set<String> existing;
        try {
            existing = template.getCollectionNames();
        } catch (DataAccessException | MongoException ex) {
            return Report.skipped(rootMessage(ex));
        }

        List<String> created = createMissing(byCollection.keySet(), existing, issues);
        unmappedCollections = existing.stream()
                .filter(name -> !byCollection.containsKey(name))
                .sorted()
                .toList();

        checkSampleDocuments(byCollection, entitiesByType, issues);
        return new Report(created, issues, false, null);
    }

    private Map<String, List<MongoPersistentEntity<?>>> groupMappedEntities(List<Issue> issues) {
        Map<String, List<MongoPersistentEntity<?>>> byCollection = new LinkedHashMap<>();
        for (MongoPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
            if (!entity.getType().isAnnotationPresent(org.springframework.data.mongodb.core.mapping.Document.class)) {
                continue;
            }
            byCollection.computeIfAbsent(entity.getCollection(), name -> new ArrayList<>()).add(entity);
        }
        byCollection.forEach((collection, entities) -> {
            if (entities.size() > 1) {
                String mappedBy = entities.stream()
                        .map(entity -> entity.getType().getName())
                        .sorted()
                        .collect(Collectors.joining(", "));
                issues.add(new Issue(Kind.DUPLICATE_MAPPING, collection, null, "mapped by " + mappedBy));
            }
        });
        return byCollection;
    }

    private Map<Class<?>, MongoPersistentEntity<?>> indexEntitiesByType() {
        // Snapshot instead of getPersistentEntity(type): that method lazily registers new
        // entities, which would make later runs see value objects as root collections.
        Map<Class<?>, MongoPersistentEntity<?>> byType = new HashMap<>();
        for (MongoPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
            byType.putIfAbsent(entity.getType(), entity);
        }
        return byType;
    }

    private List<String> createMissing(Set<String> expected, Set<String> existing, List<Issue> issues) {
        List<String> created = new ArrayList<>();
        for (String collection : expected) {
            if (existing.contains(collection)) {
                continue;
            }
            issues.add(new Issue(Kind.COLLECTION_MISSING, collection, null, null));
            try {
                template.createCollection(collection);
                created.add(collection);
            } catch (DataAccessException | MongoException ex) {
                if (!isNamespaceExists(ex)) {
                    issues.add(new Issue(Kind.CREATE_FAILED, collection, null, rootMessage(ex)));
                }
            }
        }
        return created;
    }

    private void checkSampleDocuments(Map<String, List<MongoPersistentEntity<?>>> byCollection,
                                      Map<Class<?>, MongoPersistentEntity<?>> entitiesByType,
                                      List<Issue> issues) {
        for (Map.Entry<String, List<MongoPersistentEntity<?>>> entry : byCollection.entrySet()) {
            Document sample;
            try {
                sample = template.findOne(new Query(), Document.class, entry.getKey());
            } catch (DataAccessException | MongoException ex) {
                // The type check is advisory; an unreadable collection must not stop the boot.
                continue;
            }
            if (sample == null) {
                continue;
            }
            reportTypeMismatches(entry.getKey(), entry.getValue(), flatten(sample), entitiesByType, issues);
        }
    }

    private void reportTypeMismatches(String collection, List<MongoPersistentEntity<?>> entities,
                                      Map<String, Object> values,
                                      Map<Class<?>, MongoPersistentEntity<?>> entitiesByType,
                                      List<Issue> issues) {
        // A field of a duplicate-mapped collection is reported only when no entity of that
        // collection accepts the value: two entities disagreeing on a type make any verdict uncertain.
        Map<String, String> flagged = new LinkedHashMap<>();
        Set<String> accepted = new HashSet<>();
        for (MongoPersistentEntity<?> entity : entities) {
            values.forEach((path, value) -> {
                Check check = checkPath(entity, path, value, entitiesByType);
                if (check.verdict() == Verdict.MISMATCH) {
                    flagged.putIfAbsent(path, check.detail());
                } else if (check.verdict() == Verdict.OK) {
                    accepted.add(path);
                }
            });
        }
        flagged.forEach((path, detail) -> {
            if (!accepted.contains(path)) {
                issues.add(new Issue(Kind.TYPE_MISMATCH, collection, path, detail));
            }
        });
    }

    private Map<String, Object> flatten(Document sample) {
        Map<String, Object> values = new LinkedHashMap<>();
        sample.forEach((key, value) -> {
            if (CLASS_FIELD.equals(key) || value == null) {
                return;
            }
            values.put(key, value);
            if (value instanceof Document nested) {
                nested.forEach((nestedKey, nestedValue) -> {
                    if (!CLASS_FIELD.equals(nestedKey) && nestedValue != null) {
                        values.put(key + "." + nestedKey, nestedValue);
                    }
                });
            }
        });
        return values;
    }

    private Check checkPath(MongoPersistentEntity<?> entity, String path, Object value,
                            Map<Class<?>, MongoPersistentEntity<?>> entitiesByType) {
        String[] segments = path.split("\\.", 2);
        MongoPersistentProperty property = findProperty(entity, segments[0]);
        if (property == null) {
            return Check.NO_OPINION;
        }
        if (segments.length == 1) {
            return check(property, value);
        }
        if (property.isMap() || property.isCollectionLike()) {
            return Check.NO_OPINION;
        }
        MongoPersistentEntity<?> nested = entitiesByType.get(property.getActualType());
        if (nested == null) {
            return Check.NO_OPINION;
        }
        MongoPersistentProperty nestedProperty = findProperty(nested, segments[1]);
        return nestedProperty == null ? Check.NO_OPINION : check(nestedProperty, value);
    }

    private MongoPersistentProperty findProperty(MongoPersistentEntity<?> entity, String fieldName) {
        if (ID_FIELD.equals(fieldName)) {
            MongoPersistentProperty idProperty = entity.getIdProperty();
            if (idProperty != null) {
                return idProperty;
            }
        }
        for (MongoPersistentProperty property : entity) {
            if (property.getFieldName().equals(fieldName)) {
                return property;
            }
        }
        return null;
    }

    private Check check(MongoPersistentProperty property, Object value) {
        Class<?> type = property.getType();

        if (type == String.class) {
            if (value instanceof String) {
                return Check.OK;
            }
            if (value instanceof ObjectId) {
                return ObjectId.class.equals(property.getFieldType()) ? Check.OK
                        : Check.mismatch("String", value);
            }
            return Check.mismatch("String", value);
        }
        if (type == Integer.class || type == int.class) {
            if (value instanceof Integer) {
                return Check.OK;
            }
            if (value instanceof Long longValue
                    && longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return Check.OK;
            }
            return Check.mismatch("Integer", value);
        }
        if (type == Long.class || type == long.class) {
            return value instanceof Long || value instanceof Integer
                    ? Check.OK : Check.mismatch("Long", value);
        }
        if (type == Double.class || type == double.class || type == Float.class || type == float.class
                || type == BigDecimal.class) {
            return value instanceof Double || value instanceof Decimal128
                    || value instanceof Integer || value instanceof Long
                    ? Check.OK : Check.mismatch("Double", value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return value instanceof Boolean ? Check.OK : Check.mismatch("Boolean", value);
        }
        if (type == Instant.class || type == Date.class || type == LocalDateTime.class
                || type == ZonedDateTime.class || type == OffsetDateTime.class) {
            return value instanceof Date ? Check.OK : Check.mismatch("Date", value);
        }
        if (type.isEnum()) {
            return value instanceof String ? Check.OK : Check.mismatch("String", value);
        }
        if (property.isCollectionLike()) {
            return value instanceof List ? Check.OK : Check.mismatch("List", value);
        }
        if (property.isMap()) {
            return value instanceof Document ? Check.OK : Check.mismatch("Document", value);
        }
        if (!MongoSimpleTypes.HOLDER.isSimpleType(type) && !isFrameworkOrJdkType(type)) {
            return value instanceof Document ? Check.OK : Check.mismatch("Document", value);
        }
        return Check.NO_OPINION;
    }

    // JDK, driver and bson types written through built-in converters (e.g. java.time values)
    // are neither simple per MongoSimpleTypes nor embedded Documents; never guess with them.
    private static boolean isFrameworkOrJdkType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("jdk.") || name.startsWith("org.bson.") || name.startsWith("com.mongodb.");
    }

    private static boolean isNamespaceExists(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof MongoException mongoException
                    && mongoException.getCode() == NAMESPACE_EXISTS_ERROR_CODE) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private static String bsonName(Object value) {
        if (value instanceof Integer) {
            return "Int32";
        }
        if (value instanceof Long) {
            return "Int64";
        }
        if (value instanceof Double) {
            return "Double";
        }
        if (value instanceof Decimal128) {
            return "Decimal128";
        }
        if (value instanceof String) {
            return "String";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof Date) {
            return "Date";
        }
        if (value instanceof ObjectId) {
            return "ObjectId";
        }
        if (value instanceof Document) {
            return "Document";
        }
        if (value instanceof List) {
            return "List";
        }
        return value.getClass().getSimpleName();
    }

    private enum Verdict {
        NO_OPINION, OK, MISMATCH
    }

    private record Check(Verdict verdict, String detail) {
        static final Check NO_OPINION = new Check(Verdict.NO_OPINION, null);
        static final Check OK = new Check(Verdict.OK, null);

        static Check mismatch(String expected, Object actual) {
            return new Check(Verdict.MISMATCH, "expected " + expected + " but found " + bsonName(actual));
        }
    }
}
