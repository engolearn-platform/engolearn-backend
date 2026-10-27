package fit.iuh.engolearn.shared.security.impl;

import fit.iuh.engolearn.shared.security.CurrentUser;
import fit.iuh.engolearn.shared.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsEmptyWhenThereIsNoAuthentication() {
        assertThat(provider.getCurrentUser()).isEmpty();
    }

    @Test
    void returnsTheAuthenticatedUserFromTheSecurityContext() {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal("68f8f3a9b9e4a12345678901", "dev.user@engolearn.local", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        Optional<CurrentUser> currentUser = provider.getCurrentUser();

        assertThat(currentUser).contains(new CurrentUser(
                "68f8f3a9b9e4a12345678901", "dev.user@engolearn.local", UserRole.USER));
    }

    @Test
    void ignoresAnAuthenticationWithAnUnknownPrincipalType() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertThat(provider.getCurrentUser()).isEmpty();
    }
}
