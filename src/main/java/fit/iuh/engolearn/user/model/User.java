package fit.iuh.engolearn.user.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.UserRole;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@Document("users")
public class User extends BaseDocument {
    @Indexed(unique = true)
    @Field("email") private String email;
    @Field("password_hash") private String passwordHash;
    @Field("role") private UserRole role;
    @Field("locked") private boolean locked;

    public User(String id, String email, String passwordHash, UserRole role, boolean locked) {
        setId(id); this.email = email; this.passwordHash = passwordHash;
        this.role = role == null ? UserRole.USER : role; this.locked = locked;
    }
}
