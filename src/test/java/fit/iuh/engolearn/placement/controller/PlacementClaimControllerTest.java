package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlacementAttemptController.class)
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class,
        AccessCookieAuthFilter.class, JwtTokenService.class, PlacementCookieAuthSupport.class,
        InMemoryPlacementAttemptRepository.class, SamplePlacementTestSetRepository.class,
        PlacementAttemptServiceImpl.class, PlacementDeadlineServiceImpl.class,
        PlacementClaimServiceImpl.class, PlacementClaimControllerTest.ClockConfig.class})
@TestPropertySource(properties = "engolearn.security.jwt.secret=placement-cookie-auth-test-key-32-bytes")
class PlacementClaimControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private ObjectMapper mapper;
    @Autowired private InMemoryPlacementAttemptRepository repository;
    @Autowired private JwtTokenService tokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private RefreshSessionRepository sessions;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void guestStartSaveSubmitThenAuthenticatedLazyClaimReturnsTrustedAttemptId() throws Exception {
        var created = mvc.perform(post("/api/v1/placement/attempts"))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).path("data").path("attemptId").asText();
        String token = created.getResponse().getHeader("Set-Cookie").split("[=;]")[1];
        Cookie cookie = new Cookie("placement_guest_session", token);

        mvc.perform(patch("/api/v1/placement/attempts/{id}/answers", id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"q01\",\"selectedOptionId\":\"q01-a\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/placement/attempts/{id}/submit", id).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        var gate = mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id).cookie(cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.claimStatus").value("CLAIMABLE"))
                .andExpect(jsonPath("$.data.resultAttemptId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.claimExpiresAt").value("2026-01-01T02:00:00Z"))
                .andExpect(jsonPath("$.data.serverNow").exists()).andReturn();
        assertThat(gate.getResponse().getContentAsString()).doesNotContain("score", "placementLevel",
                "correctOptionId", "explanation", "transcript", "questions", "prompt", "summary");
        Cookie authenticated = PlacementCookieAuthSupport.accessCookie("68f8f3a9b9e4a12345678901",
                UserRole.USER, users, sessions, tokens);

        mvc.perform(post("/api/v1/placement/attempts").cookie(cookie)
                        .cookie(authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptId").value(id))
                .andExpect(jsonPath("$.data.claimRequired").value(true));

        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id).cookie(cookie, authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.claimStatus").value("ALREADY_CLAIMED"))
                .andExpect(jsonPath("$.data.resultAttemptId").value(id))
                .andExpect(jsonPath("$.data.score").doesNotExist());
        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id).cookie(authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resultAttemptId").value(id));
        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id).cookie(cookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLACEMENT_ATTEMPT_NOT_FOUND"));
    }

    @Test
    void deadlineWithoutAnswersReturnsNoResultAndMissingOrWrongCookieReturns404() throws Exception {
        var created = mvc.perform(post("/api/v1/placement/attempts")).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).path("data").path("attemptId").asText();
        Cookie cookie = new Cookie("placement_guest_session", created.getResponse().getHeader("Set-Cookie").split("[=;]")[1]);
        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id)
                        .cookie(new Cookie("placement_guest_session", "wrong")))
                .andExpect(status().isNotFound());
        clock.set(clock.instant().plus(Duration.ofMinutes(15)));
        mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", id).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.claimStatus").value("NO_RESULT"))
                .andExpect(jsonPath("$.data.claimExpiresAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.resultAttemptId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void activeUserAttemptReturnsConflictWithoutClaimingOrLeakingGuestResult() throws Exception {
        Cookie authenticated = PlacementCookieAuthSupport.accessCookie("68f8f3a9b9e4a12345678902",
                UserRole.USER, users, sessions, tokens);
        String userAttemptId = mapper.readTree(mvc.perform(post("/api/v1/placement/attempts")
                        .cookie(authenticated))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("data").path("attemptId").asText();

        var guestStart = mvc.perform(post("/api/v1/placement/attempts"))
                .andExpect(status().isCreated()).andReturn();
        String guestAttemptId = mapper.readTree(guestStart.getResponse().getContentAsString())
                .path("data").path("attemptId").asText();
        Cookie cookie = new Cookie("placement_guest_session",
                guestStart.getResponse().getHeader("Set-Cookie").split("[=;]")[1]);

        mvc.perform(patch("/api/v1/placement/attempts/{id}/answers", guestAttemptId).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"q01\",\"selectedOptionId\":\"q01-a\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/placement/attempts/{id}/submit", guestAttemptId).cookie(cookie))
                .andExpect(status().isOk());
        Instant claimExpiresAt = repository.findById(guestAttemptId).orElseThrow().getClaimExpiresAt();

        var conflict = mvc.perform(get("/api/v1/placement/attempts/{id}/claim-gate", guestAttemptId)
                        .cookie(cookie, authenticated))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PLACEMENT_ACTIVE_ATTEMPT_EXISTS"))
                .andExpect(jsonPath("$.detail").exists())
                .andReturn();
        assertThat(conflict.getResponse().getContentAsString()).doesNotContain("userId", "guestSessionId",
                "score", "placementLevel", "correctOptionId");

        assertThat(repository.findById(userAttemptId).orElseThrow().getStatus())
                .isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        var guest = repository.findById(guestAttemptId).orElseThrow();
        assertThat(guest.getUserId()).isNull();
        assertThat(guest.getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(guest.getClaimExpiresAt()).isEqualTo(claimExpiresAt);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean MutableClock placementTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

        void set(Instant value) { time.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
