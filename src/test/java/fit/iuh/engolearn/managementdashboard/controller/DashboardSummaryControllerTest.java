package fit.iuh.engolearn.managementdashboard.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.grammar.contract.GrammarManagementSummaryProvider;
import fit.iuh.engolearn.grammar.contract.dto.GrammarManagementSummaryDto;
import fit.iuh.engolearn.learningpath.contract.LearningPathManagementSummaryProvider;
import fit.iuh.engolearn.learningpath.contract.dto.LearningPathManagementSummaryDto;
import fit.iuh.engolearn.managementdashboard.controller.exceptionhandler.DashboardSummaryExceptionHandler;
import fit.iuh.engolearn.managementdashboard.service.DashboardSummaryService;
import fit.iuh.engolearn.managementdashboard.service.impl.DashboardSummaryServiceImpl;
import fit.iuh.engolearn.placement.contract.PlacementManagementSummaryProvider;
import fit.iuh.engolearn.placement.contract.dto.PlacementManagementSummaryDto;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.subscription.contract.SubscriptionManagementSummaryProvider;
import fit.iuh.engolearn.topic.contract.TopicManagementSummaryProvider;
import fit.iuh.engolearn.topic.contract.dto.TopicManagementSummaryDto;
import fit.iuh.engolearn.topic.support.TopicTestAuthentication;
import fit.iuh.engolearn.user.contract.UserManagementSummaryProvider;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardSummaryController.class)
@Import({SecurityConfig.class, DashboardSummaryServiceImpl.class, DashboardSummaryExceptionHandler.class})
class DashboardSummaryControllerTest {
    private static final String PATH = "/api/management/dashboard/summary";
    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

    @Autowired private MockMvc mvc;
    @MockitoBean private Clock clock;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private UserRepository usersRepository;
    @MockitoBean private RefreshSessionRepository sessions;
    @MockitoBean private TopicManagementSummaryProvider topics;
    @MockitoBean private GrammarManagementSummaryProvider grammar;
    @MockitoBean private LearningPathManagementSummaryProvider learningPath;
    @MockitoBean private UserManagementSummaryProvider users;
    @MockitoBean private SubscriptionManagementSummaryProvider subscriptions;
    @MockitoBean private PlacementManagementSummaryProvider placement;

    @Test
    void guestAndUserAreRejectedBeforeAnySummaryProviderRuns() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(PATH).with(role(UserRole.USER))).andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(topics, grammar, learningPath, users, subscriptions, placement);
    }

    @Test
    void contentManagerGetsOnlyContentAndResponseIsNotCached() throws Exception {
        contentCounts();
        when(clock.instant()).thenReturn(NOW);

        mvc.perform(get(PATH).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.content.topics.total").value(3))
                .andExpect(jsonPath("$.data.content.grammar.total").value(4))
                .andExpect(jsonPath("$.data.content.passages.total").value(5))
                .andExpect(jsonPath("$.data.content.learningPathQuestions.total").value(6))
                .andExpect(jsonPath("$.data.administration").doesNotExist())
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()));
        verifyNoInteractions(users, subscriptions, placement);
    }

    @Test
    void adminGetsAdministrationAndSourceFailuresUseProblemDetail() throws Exception {
        contentCounts();
        when(clock.instant()).thenReturn(NOW);
        when(users.managementAccountCount()).thenReturn(7L);
        when(subscriptions.countActivePaidPremiumUsersAt(NOW)).thenReturn(8L);
        when(placement.managementSummary()).thenReturn(new PlacementManagementSummaryDto(9, 10));

        mvc.perform(get(PATH).with(role(UserRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.administration.users.total").value(7))
                .andExpect(jsonPath("$.data.administration.subscription.activePremium").value(8))
                .andExpect(jsonPath("$.data.administration.placement.testSets").value(9))
                .andExpect(jsonPath("$.data.administration.placement.questions").value(10));

        when(topics.managementSummary()).thenThrow(new UncategorizedDataAccessException("store failed", null) { });
        mvc.perform(get(PATH).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isServiceUnavailable()).andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("DASHBOARD_SOURCE_UNAVAILABLE"));
    }

    private void contentCounts() {
        when(topics.managementSummary()).thenReturn(new TopicManagementSummaryDto(3, 1, 1, 1));
        when(grammar.managementSummary()).thenReturn(new GrammarManagementSummaryDto(4, 1, 1, 1, 1));
        when(learningPath.managementSummary()).thenReturn(new LearningPathManagementSummaryDto(5, 2, 2, 1, 6));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("dashboard-test", "dashboard@test", role);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return TopicTestAuthentication.authentication(authentication);
    }
}
