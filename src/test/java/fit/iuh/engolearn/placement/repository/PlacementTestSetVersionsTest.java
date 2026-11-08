package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlacementTestSetVersionsTest {
    @Test void keepsLatestVersionOfEachCodeWithoutMutatingHistoricalSets() {
        var old = set("a-v1", "A", 1);
        var latest = set("a-v2", "A", 2);
        var other = set("b-v1", "B", 1);
        assertThat(PlacementTestSetVersions.latest(List.of(old, latest, other)))
                .extracting(PlacementTestSet::getId).containsExactly("a-v2", "b-v1");
        assertThat(old.getVersion()).isEqualTo(1);
    }
    @Test void retainsLegacySetsWithoutCodesAndHandlesEmptyInput() {
        assertThat(PlacementTestSetVersions.latest(List.of())).isEmpty();
        assertThat(PlacementTestSetVersions.latest(List.of(set("one", null, 1), set("two", null, 2))))
                .hasSize(2);
    }
    private PlacementTestSet set(String id, String code, int version) {
        return PlacementTestSet.builder().id(id).code(code).version(version).build();
    }
}
