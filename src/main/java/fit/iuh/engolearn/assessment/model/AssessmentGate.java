package fit.iuh.engolearn.assessment.model;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@lombok.experimental.SuperBuilder
@Document("assessment_gates")
@CompoundIndex(name="assessment_gate_owner", def="{'user_id':1}", unique=true)
public class AssessmentGate extends fit.iuh.engolearn.shared.BaseDocument {
    @Field("user_id") private String userId;
    @Field("revision") private long revision;
}
