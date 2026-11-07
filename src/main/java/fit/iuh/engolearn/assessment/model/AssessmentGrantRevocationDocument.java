package fit.iuh.engolearn.assessment.model;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@lombok.experimental.SuperBuilder
@Document("assessment_grant_revocations")
@CompoundIndex(name="assessment_revocation_source", def="{'user_id':1,'source_id':1}", unique=true)
public class AssessmentGrantRevocationDocument extends fit.iuh.engolearn.shared.BaseDocument {
    @Field("user_id") private String userId;
    @Field("source_id") private String sourceId;
}
