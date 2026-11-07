package fit.iuh.engolearn.placement.model;

import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import fit.iuh.engolearn.shared.BaseDocument;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("placement_test_sets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PlacementTestSet extends BaseDocument {
    @Field("code") private String code;
    @Field("name") private String name;
    @Field("version") private int version;
    @Field("status") private PlacementTestSetStatus status;
    @Field("items") private List<PlacementSnapshotItemVo> items;
}
