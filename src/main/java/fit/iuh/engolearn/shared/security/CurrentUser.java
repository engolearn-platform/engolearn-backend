package fit.iuh.engolearn.shared.security;

import fit.iuh.engolearn.shared.enums.UserRole;

public record CurrentUser(String id, String email, UserRole role) {
}
