package fit.iuh.engolearn.shared.security.impl;

import fit.iuh.engolearn.shared.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserPrincipalTest {

    @Test
    void userRoleGetsOnlyTheUserAuthority() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal("id", "user@example.com", UserRole.USER);

        assertThat(authorities(principal)).containsExactly("ROLE_USER");
    }

    @Test
    void contentManagerRoleGetsTheUserAndContentManagerAuthorities() {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal("id", "cm@example.com", UserRole.CONTENT_MANAGER);

        assertThat(authorities(principal)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_CONTENT_MANAGER");
    }

    @Test
    void adminRoleGetsTheFullSupersetOfAuthorities() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal("id", "admin@example.com", UserRole.ADMIN);

        assertThat(authorities(principal))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_CONTENT_MANAGER", "ROLE_ADMIN");
    }

    private Set<String> authorities(AuthenticatedUserPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }
}
