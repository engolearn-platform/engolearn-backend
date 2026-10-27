package fit.iuh.engolearn.shared.security.dev;

import fit.iuh.engolearn.shared.enums.UserRole;

import java.util.Map;

public class DevUserFixture {
    private final String id;
    private final String email;
    private final UserRole role;

    public DevUserFixture(String id, String email, UserRole role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public static Map<String, DevUserFixture> defaultFixtures() {
        return Map.of(
                "dev-user", new DevUserFixture("68f8f3a9b9e4a12345678901", "dev.user@engolearn.local", UserRole.USER),
                "dev-cm", new DevUserFixture("68f8f3a9b9e4a12345678902", "dev.cm@engolearn.local", UserRole.CONTENT_MANAGER),
                "dev-admin", new DevUserFixture("68f8f3a9b9e4a12345678903", "dev.admin@engolearn.local", UserRole.ADMIN)
        );
    }
}
