package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.controller.exceptionhandler.PlacementExceptionHandler;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlacementAttemptControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        var attempts = new InMemoryPlacementAttemptRepository();
        var sets = new SamplePlacementTestSetRepository(new ClassPathResource("placement/sample-test-set.json"),
                new ObjectMapper());
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var deadlines = new PlacementDeadlineServiceImpl(attempts, sets);
        var service = new PlacementAttemptServiceImpl(attempts, sets, clock, deadlines);
        var claimService = new PlacementClaimServiceImpl(attempts, deadlines, clock);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.getCurrentUser()).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new PlacementAttemptController(service, claimService, users))
                .setControllerAdvice(new PlacementExceptionHandler()).build();
    }

    @Test
    void guestStartIssuesHttpOnlyCookieAndResumeReturns200WithSameDeadline() throws Exception {
        var created = mvc.perform(post("/api/v1/placement/attempts"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/placement"))))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.claimRequired").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();
        String id = new ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("data").path("attemptId").asText();
        String cookieValue = created.getResponse().getHeader("Set-Cookie").split("[=;]")[1];
        mvc.perform(post("/api/v1/placement/attempts").cookie(new Cookie("placement_guest_session", cookieValue)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptId").value(id))
                .andExpect(jsonPath("$.data.deadlineAt").value("2026-01-01T00:15:00Z"));
        mvc.perform(get("/api/v1/placement/attempts/{id}", id)
                        .cookie(new Cookie("placement_guest_session", cookieValue)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(20))
                .andExpect(jsonPath("$.data.serverNow").exists())
                .andExpect(jsonPath("$.data.questions[0].options[0].text").value("is"));
        mvc.perform(get("/api/v1/placement/attempts/{id}", id)
                        .cookie(new Cookie("placement_guest_session", "wrong")))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PLACEMENT_ATTEMPT_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/placement/attempts/" + id));
    }

    @Test
    void serializedActivePayloadNeverContainsPrivateAnswerOrResultFields() throws Exception {
        var created = mvc.perform(post("/api/v1/placement/attempts")).andReturn();
        String id = new ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("data").path("attemptId").asText();
        String cookieValue = created.getResponse().getHeader("Set-Cookie").split("[=;]")[1];
        String json = mvc.perform(get("/api/v1/placement/attempts/{id}", id)
                        .cookie(new Cookie("placement_guest_session", cookieValue)))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("correctOptionId", "correct_option_id", "correctCount", "score",
                "placementLevel", "explanation", "transcript", "guestSessionId", "userId");
    }

    @Test
    void httpValidationAndZeroAnswerSubmitReturnProblemDetail() throws Exception {
        var created = mvc.perform(post("/api/v1/placement/attempts")).andReturn();
        String id = new ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("data").path("attemptId").asText();
        Cookie cookie = new Cookie("placement_guest_session", created.getResponse().getHeader("Set-Cookie").split("[=;]")[1]);
        mvc.perform(patch("/api/v1/placement/attempts/{id}/answers", id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"questionId\":\"\",\"selectedOptionId\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLACEMENT_INVALID_REQUEST"));
        mvc.perform(post("/api/v1/placement/attempts/{id}/submit", id).cookie(cookie))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLACEMENT_NO_ANSWER_TO_SUBMIT"));
        mvc.perform(patch("/api/v1/placement/attempts/{id}/answers", id).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"q01\",\"selectedOptionId\":\"q01-a\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedOptionId").value("q01-a"));
        mvc.perform(post("/api/v1/placement/attempts/{id}/submit", id).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.score").doesNotExist());
    }
}
