package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptPageResponse;
import fit.iuh.engolearn.placement.service.PlacementAdminAttemptService;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlacementAdminAttemptController.class)
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class,
        AccessCookieAuthFilter.class, JwtTokenService.class, PlacementCookieAuthSupport.class})
@TestPropertySource(properties = "engolearn.security.jwt.secret=placement-admin-cookie-auth-test-key-32-bytes")
class PlacementAdminCookieAuthTest {
    private static final String ADMIN_PATH = "/api/v1/placement/admin/attempts";
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;
    @MockitoBean private PlacementAdminAttemptService attempts;

    @Test
    void guestCannotReadAdminPlacement() throws Exception {
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedNonAdminsCannotReadAdminPlacement() throws Exception {
        for (UserRole role : new UserRole[]{UserRole.USER, UserRole.CONTENT_MANAGER}) {
            mvc.perform(get(ADMIN_PATH).cookie(PlacementCookieAuthSupport.accessCookie(
                            "68f8f3a9b9e4a12345678901", role, users, sessions, tokens)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void adminCanReadAdminPlacement() throws Exception {
        when(attempts.search(any())).thenReturn(new PlacementAdminAttemptPageResponse(List.of(), 0, 0, 20));
        mvc.perform(get(ADMIN_PATH).cookie(PlacementCookieAuthSupport.accessCookie(
                        "68f8f3a9b9e4a12345678901", UserRole.ADMIN, users, sessions, tokens)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
