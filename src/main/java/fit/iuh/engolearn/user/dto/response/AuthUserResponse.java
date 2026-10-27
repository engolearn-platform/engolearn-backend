package fit.iuh.engolearn.user.dto.response;

import fit.iuh.engolearn.shared.enums.UserRole;

public record AuthUserResponse(String id, String email, UserRole role) {
}
