package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.placement.dto.response.StartPlacementAttemptResponse;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.service.PlacementAccessContext;
import fit.iuh.engolearn.placement.service.PlacementAttemptService;
import fit.iuh.engolearn.placement.service.PlacementClaimService;
import fit.iuh.engolearn.placement.service.PlacementStartOutcome;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlacementAttemptController.class)
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class,
        AccessCookieAuthFilter.class, JwtTokenService.class, PlacementCookieAuthSupport.class})
@TestPropertySource(properties = "engolearn.security.jwt.secret=placement-cookie-auth-test-key-32-bytes")
class PlacementCookieAuthTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockitoBean private PlacementAttemptService service;
    @MockitoBean private PlacementClaimService claimService;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;

    @Test
    void accessCookieGivesPlacementAuthenticatedUserWithoutGuestCookie() throws Exception {
        when(service.start(any())).thenReturn(new PlacementStartOutcome(new StartPlacementAttemptResponse(
                "user-attempt", PlacementAttemptStatus.IN_PROGRESS, Instant.parse("2026-01-01T00:15:00Z"), false), true));
        mvc.perform(post("/api/v1/placement/attempts").cookie(PlacementCookieAuthSupport.accessCookie(
                        "68f8f3a9b9e4a12345678901", UserRole.USER, users, sessions, tokens)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data.claimRequired").value(false));
        verify(service).start(new PlacementAccessContext("68f8f3a9b9e4a12345678901", null));
    }

    @Test
    void legacyDevHeaderDoesNotAuthenticatePlacement() throws Exception {
        when(service.isKnownGuestSession(any())).thenReturn(false);
        when(service.start(any())).thenReturn(new PlacementStartOutcome(new StartPlacementAttemptResponse(
                "guest-attempt", PlacementAttemptStatus.IN_PROGRESS, Instant.parse("2026-01-01T00:15:00Z"), true), true));
        mvc.perform(post("/api/v1/placement/attempts").header("X-Dev-User-Id", "dev-user"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.claimRequired").value(true));
        verify(service).start(org.mockito.ArgumentMatchers.argThat(access ->
                access.currentUserId() == null && access.guestSessionId() != null));
    }
}
