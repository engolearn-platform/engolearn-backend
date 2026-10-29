package fit.iuh.engolearn.shared.security.impl;

import fit.iuh.engolearn.shared.security.CurrentUser;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
            return Optional.of(new CurrentUser(
                    authenticatedUserPrincipal.getId(),
                    authenticatedUserPrincipal.getEmail(),
                    authenticatedUserPrincipal.getRole()
            ));
        }
        return Optional.empty();
    }
}


