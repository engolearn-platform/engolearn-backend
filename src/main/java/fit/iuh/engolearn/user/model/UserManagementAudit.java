package fit.iuh.engolearn.user.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.enums.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@Document("user_management_audit")
public class UserManagementAudit extends BaseDocument {
    @Field("actor_id") private String actorId;
    @Field("target_id") private String targetId;
    @Field("action") private ManagementAction action;
    @Field("before_role") private UserRole beforeRole;
    @Field("after_role") private UserRole afterRole;
    @Field("before_status") private ManagementStatus beforeStatus;
    @Field("after_status") private ManagementStatus afterStatus;
}
