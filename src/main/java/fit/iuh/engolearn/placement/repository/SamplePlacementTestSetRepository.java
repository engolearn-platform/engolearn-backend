package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode",
        havingValue = "sample", matchIfMissing = true)
public class SamplePlacementTestSetRepository implements PlacementTestSetRepository {
    private final PlacementTestSet sample;

    public SamplePlacementTestSetRepository(@Value("classpath:placement/sample-test-set.json") Resource resource,
                                             ObjectMapper mapper) throws IOException {
        this.sample = mapper.readValue(resource.getInputStream(), PlacementTestSet.class);
        validate(sample);
    }

    @Override
    public Optional<PlacementTestSet> findPublished() {
        return sample.getStatus() == PlacementTestSetStatus.PUBLISHED
                ? Optional.of(PlacementCopies.testSet(sample)) : Optional.empty();
    }

    @Override
    public Optional<PlacementTestSet> findVersion(String id, int version) {
        return sample.getId().equals(id) && sample.getVersion() == version
                ? Optional.of(PlacementCopies.testSet(sample)) : Optional.empty();
    }

    private static void validate(PlacementTestSet set) {
        if (set.getStatus() != PlacementTestSetStatus.PUBLISHED || set.getId() == null
                || set.getVersion() < 1 || set.getItems() == null || set.getItems().size() != 20) {
            throw new IllegalStateException("Invalid published Placement sample set");
        }
        Map<PlacementLevel, int[]> blueprint = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            PlacementSnapshotItemVo item = set.getItems().get(i);
            PlacementSkill requiredSkill = i < 14 ? PlacementSkill.READING : PlacementSkill.LISTENING;
            if (item.getOrder() != i + 1 || item.getSkill() != requiredSkill || item.getLevel() == null
                    || item.getId() == null || !ids.add(item.getId()) || item.getPrompt() == null
                    || item.getPrompt().isBlank() || item.getOptions() == null || item.getOptions().size() != 4
                    || (requiredSkill == PlacementSkill.LISTENING && item.getAudio() == null)) {
                throw new IllegalStateException("Invalid Placement sample question at order " + (i + 1));
            }
            Set<String> optionIds = new HashSet<>();
            Set<String> labels = new HashSet<>();
            item.getOptions().forEach(o -> {
                if (o.getId() == null || !optionIds.add(o.getId()) || o.getLabel() == null
                        || !labels.add(o.getLabel()) || o.getText() == null || o.getText().isBlank()) {
                    throw new IllegalStateException("Invalid Placement sample option");
                }
            });
            if (!labels.equals(Set.of("A", "B", "C", "D")) || !optionIds.contains(item.getCorrectOptionId())) {
                throw new IllegalStateException("Invalid Placement sample correct option");
            }
            blueprint.computeIfAbsent(item.getLevel(), ignored -> new int[2])[i < 14 ? 0 : 1]++;
        }
        for (PlacementLevel level : PlacementLevel.values()) {
            int[] count = blueprint.get(level);
            int reading = level == PlacementLevel.A1 || level == PlacementLevel.A2 ? 4 : 3;
            if (count == null || count[0] != reading || count[1] != 5 - reading) {
                throw new IllegalStateException("Invalid Placement sample blueprint at " + level);
            }
        }
    }
}
