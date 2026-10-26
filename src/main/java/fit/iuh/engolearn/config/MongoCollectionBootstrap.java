package fit.iuh.engolearn.config;

import fit.iuh.engolearn.config.MongoCollectionInspector.Issue;
import fit.iuh.engolearn.config.MongoCollectionInspector.Kind;
import fit.iuh.engolearn.config.MongoCollectionInspector.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the collection guard once at startup; every failure is logged and the boot continues.
 * Disable with {@code engolearn.mongodb.bootstrap.enabled=false}.
 */
@Configuration
public class MongoCollectionBootstrap {

    private static final Logger log = LoggerFactory.getLogger(MongoCollectionBootstrap.class);

    private static final int MAX_WARN_LINES = 20;

    @Bean
    @ConditionalOnProperty(prefix = "engolearn.mongodb.bootstrap", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApplicationRunner mongoCollectionBootstrapRunner(MongoTemplate mongoTemplate,
                                                            MongoMappingContext mongoMappingContext) {
        return args -> runOnce(mongoTemplate, mongoMappingContext);
    }

    static void runOnce(MongoTemplate mongoTemplate, MongoMappingContext mongoMappingContext) {
        MongoCollectionInspector inspector = new MongoCollectionInspector(mongoTemplate, mongoMappingContext);
        Report report;
        try {
            report = inspector.inspectAndCreateMissing();
        } catch (RuntimeException ex) {
            log.warn("mongo collection check failed: {}", ex.getMessage());
            return;
        }
        if (report.skipped()) {
            log.warn("mongo collection check skipped: {}", report.skipReason());
            return;
        }
        log.info("mongo collection check: {} entities / {} collections / created {} / issues {}",
                countMappedEntities(mongoMappingContext), countMappedCollections(mongoMappingContext),
                report.collectionsCreated().size(), report.issues().size());
        renderedIssueLines(report.issues()).forEach(log::warn);
        inspector.unmappedCollections().forEach(name ->
                log.debug("mongo collection check: collection '{}' is not mapped by any entity", name));
    }

    // A create that succeeded needs no WARN, and a create that failed already surfaces as
    // its own CREATE_FAILED issue.
    static List<String> renderedIssueLines(List<Issue> issues) {
        List<Issue> warnable = issues.stream()
                .filter(issue -> issue.kind() != Kind.COLLECTION_MISSING)
                .toList();
        List<String> lines = new ArrayList<>();
        warnable.stream().limit(MAX_WARN_LINES)
                .map(MongoCollectionBootstrap::renderIssueLine)
                .forEach(lines::add);
        if (warnable.size() > MAX_WARN_LINES) {
            lines.add("mongo collection check: ... and " + (warnable.size() - MAX_WARN_LINES) + " more");
        }
        return List.copyOf(lines);
    }

    private static String renderIssueLine(Issue issue) {
        if (issue.field() == null) {
            return "mongo collection check: [" + issue.kind() + "] " + issue.collection()
                    + ": " + issue.detail();
        }
        return "mongo collection check: [" + issue.kind() + "] " + issue.collection()
                + " field " + issue.field() + ": " + issue.detail();
    }

    private static long countMappedEntities(MongoMappingContext context) {
        return context.getPersistentEntities().stream()
                .filter(entity -> entity.getType().isAnnotationPresent(Document.class))
                .count();
    }

    private static long countMappedCollections(MongoMappingContext context) {
        return context.getPersistentEntities().stream()
                .filter(entity -> entity.getType().isAnnotationPresent(Document.class))
                .map(MongoPersistentEntity::getCollection)
                .distinct()
                .count();
    }
}
