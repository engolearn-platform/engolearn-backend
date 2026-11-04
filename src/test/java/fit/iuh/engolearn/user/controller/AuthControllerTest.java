package fit.iuh.engolearn.user.controller;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.CurrentUser;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.user.service.UserAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void meDisablesCachingOfCurrentIdentity() {
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser("account", "student@example.com", UserRole.USER));
        AuthController controller = new AuthController(users, mock(UserAuthService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.me(response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
