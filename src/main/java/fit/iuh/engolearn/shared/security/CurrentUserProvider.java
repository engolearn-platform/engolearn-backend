package fit.iuh.engolearn.shared.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> getCurrentUser();

    default String requireCurrentUserId() {
        return requireCurrentUser().id();
    }

    default CurrentUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(() -> new UnauthenticatedException("Authentication is required"));
    }
}
