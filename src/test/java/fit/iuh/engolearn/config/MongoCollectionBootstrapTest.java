package fit.iuh.engolearn.config;

import fit.iuh.engolearn.config.MongoCollectionInspector.Issue;
import fit.iuh.engolearn.config.MongoCollectionInspector.Kind;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoCollectionBootstrapTest {

    @Test
    void renderedIssueLinesCapsWarningsAndSummarisesTheOverflow() {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            issues.add(new Issue(Kind.TYPE_MISMATCH, "collection_" + i, "field_" + i, "detail"));
        }
        issues.add(new Issue(Kind.COLLECTION_MISSING, "missing_collection", null, null));

        List<String> lines = MongoCollectionBootstrap.renderedIssueLines(issues);

        assertThat(lines).hasSize(21);
        assertThat(lines.get(0))
                .isEqualTo("mongo collection check: [TYPE_MISMATCH] collection_0 field field_0: detail");
        assertThat(lines.get(20)).isEqualTo("mongo collection check: ... and 5 more");
        assertThat(lines).noneMatch(line -> line.contains("COLLECTION_MISSING"));
        assertThat(lines).filteredOn(line -> line.contains("... and ")).hasSize(1);
    }

    @Test
    void runOnceSwallowsAnUnexpectedFailureFromTheInspector() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.getCollectionNames()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> MongoCollectionBootstrap.runOnce(template, emptyContext()))
                .doesNotThrowAnyException();
    }

    private static MongoMappingContext emptyContext() {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of());
        context.afterPropertiesSet();
        return context;
    }
}
