package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementResultServiceImpl;
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

@WebMvcTest(controllers = {PlacementAttemptController.class, PlacementResultController.class})
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class,
        AccessCookieAuthFilter.class, JwtTokenService.class, PlacementCookieAuthSupport.class,
        InMemoryPlacementAttemptRepository.class, SamplePlacementTestSetRepository.class,
        PlacementAttemptServiceImpl.class, PlacementDeadlineServiceImpl.class,
        PlacementClaimServiceImpl.class, PlacementResultServiceImpl.class,
        PlacementResultControllerTest.ClockConfig.class})
@TestPropertySource(properties = "engolearn.security.jwt.secret=placement-cookie-auth-test-key-32-bytes")
class PlacementResultControllerTest {
    private static final String ROOT = "/api/v1/placement/attempts";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MutableClock clock;
    @Autowired private JwtTokenService tokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private RefreshSessionRepository sessions;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void guestResultAndReviewRequireClaimThenExposeOnlyTheirOwnDtoFields() throws Exception {
        var started = mvc.perform(post(ROOT)).andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(started.getResponse().getContentAsString()).path("data").path("attemptId").asText();
        Cookie cookie = new Cookie("placement_guest_session", started.getResponse().getHeader("Set-Cookie").split("[=;]")[1]);

        String active = mvc.perform(get(ROOT + "/{id}", id).cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(active).doesNotContain("correctOptionId", "correctCount", "score", "placementLevel",
                "explanation", "transcript");

        for (String question : new String[]{"q01", "q02"}) {
            mvc.perform(patch(ROOT + "/{id}/answers", id).cookie(cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"" + question + "\",\"selectedOptionId\":\"" + question + "-a\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(ROOT + "/{id}/submit", id).cookie(cookie)).andExpect(status().isOk());

        String gate = mvc.perform(get(ROOT + "/{id}/claim-gate", id).cookie(cookie))
                .andExpect(jsonPath("$.data.claimStatus").value("CLAIMABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(gate).doesNotContain("score", "placementLevel", "correctOptionId", "questions",
                "explanation", "transcript");
        Cookie learner = PlacementCookieAuthSupport.accessCookie("68f8f3a9b9e4a12345678901",
                UserRole.USER, users, sessions, tokens);
        Cookie other = PlacementCookieAuthSupport.accessCookie("68f8f3a9b9e4a12345678903",
                UserRole.ADMIN, users, sessions, tokens);

        for (String endpoint : new String[]{"result", "review"}) {
            mvc.perform(get(ROOT + "/{id}/" + endpoint, id).cookie(cookie))
                    .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("PLACEMENT_AUTHENTICATION_REQUIRED"));
            mvc.perform(get(ROOT + "/{id}/" + endpoint, id).cookie(cookie, other))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACEMENT_ATTEMPT_NOT_FOUND"));
        }

        mvc.perform(get(ROOT + "/{id}/claim-gate", id).cookie(cookie, learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.claimStatus").value("ALREADY_CLAIMED"))
                .andExpect(jsonPath("$.data.resultAttemptId").value(id));

        var result = mvc.perform(get(ROOT + "/{id}/result", id).cookie(learner))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.score").value(2))
                .andExpect(jsonPath("$.data.placementLevel").value("A1"))
                .andExpect(jsonPath("$.data.totalQuestions").value(20))
                .andExpect(jsonPath("$.data.completedAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.summary.incorrectCount").value(0))
                .andExpect(jsonPath("$.data.summary.unansweredCount").value(18)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("correctOptionId", "selectedOptionId",
                "questions", "explanation", "transcript", "guestSessionId", "userId");

        var review = mvc.perform(get(ROOT + "/{id}/review", id).cookie(learner))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.questions.length()").value(20))
                .andExpect(jsonPath("$.data.questions[0].order").value(1))
                .andExpect(jsonPath("$.data.questions[13].section").value("READING"))
                .andExpect(jsonPath("$.data.questions[13].audio").doesNotExist())
                .andExpect(jsonPath("$.data.questions[14].section").value("LISTENING"))
                .andExpect(jsonPath("$.data.questions[0].selectedOptionId").value("q01-a"))
                .andExpect(jsonPath("$.data.questions[0].correctOptionId").value("q01-a"))
                .andExpect(jsonPath("$.data.questions[3].selectedOptionId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.questions[14].audio.url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.score").value(2))
                .andExpect(jsonPath("$.data.incorrectCount").value(0))
                .andExpect(jsonPath("$.data.unansweredCount").value(18)).andReturn();
        assertThat(review.getResponse().getContentAsString()).doesNotContain("explanation", "transcript",
                "guestSessionId", "userId");

        mvc.perform(get(ROOT + "/{id}/result", id).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/{id}/review", id).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/{id}/result", id).cookie(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredAndInProgressAuthenticatedAttemptsHaveNoResultOrReview() throws Exception {
        Cookie contentManager = PlacementCookieAuthSupport.accessCookie("68f8f3a9b9e4a12345678902",
                UserRole.CONTENT_MANAGER, users, sessions, tokens);
        String id = mapper.readTree(mvc.perform(post(ROOT).cookie(contentManager))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("data").path("attemptId").asText();
        for (String endpoint : new String[]{"result", "review"}) {
            mvc.perform(get(ROOT + "/{id}/" + endpoint, id).cookie(contentManager))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACEMENT_" + endpoint.toUpperCase() + "_NOT_AVAILABLE"));
        }
        clock.set(clock.instant().plus(Duration.ofMinutes(15)));
        for (String endpoint : new String[]{"result", "review"}) {
            mvc.perform(get(ROOT + "/{id}/" + endpoint, id).cookie(contentManager))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACEMENT_" + endpoint.toUpperCase() + "_NOT_AVAILABLE"));
        }
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
