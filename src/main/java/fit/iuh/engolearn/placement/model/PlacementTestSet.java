package fit.iuh.engolearn.placement.model;

import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import fit.iuh.engolearn.shared.BaseDocument;
import java.util.Comparator;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("placement_test_sets")
@CompoundIndexes({
        @CompoundIndex(name = "placement_set_code_version", def = "{'code': 1, 'version': 1}", unique = true),
        @CompoundIndex(name = "placement_set_editable_draft", def = "{'code': 1}", unique = true,
                partialFilter = "{'status': 'DRAFT', 'question_ids': {'$exists': true}}")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PlacementTestSet extends BaseDocument {
    @Field("code") private String code;
    @Field("name") private String name;
    @Field("version") private int version;
    @Field("status") private PlacementTestSetStatus status;
    @Field("items") private List<PlacementSnapshotItemVo> items;
    @Field("question_ids") private List<String> questionIds;
    @Field("revision") private long revision;

    public List<PlacementSnapshotItemVo> orderedItems() {
        return items == null ? List.of()
                : items.stream().sorted(Comparator.comparingInt(PlacementSnapshotItemVo::getOrder)).toList();
    }

    public List<String> orderedQuestionIds() {
        return questionIds == null ? orderedItems().stream().map(PlacementSnapshotItemVo::getId).toList()
                : List.copyOf(questionIds);
    }
}
