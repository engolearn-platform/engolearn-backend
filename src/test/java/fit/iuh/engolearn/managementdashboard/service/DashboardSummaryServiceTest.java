package fit.iuh.engolearn.managementdashboard.service;

import fit.iuh.engolearn.grammar.contract.dto.GrammarManagementSummaryDto;
import fit.iuh.engolearn.grammar.contract.GrammarManagementSummaryProvider;
import fit.iuh.engolearn.learningpath.contract.dto.LearningPathManagementSummaryDto;
import fit.iuh.engolearn.learningpath.contract.LearningPathManagementSummaryProvider;
import fit.iuh.engolearn.managementdashboard.dto.response.DashboardSummaryResponse;
import fit.iuh.engolearn.managementdashboard.service.impl.DashboardSummaryServiceImpl;
import fit.iuh.engolearn.placement.contract.PlacementManagementSummaryProvider;
import fit.iuh.engolearn.subscription.contract.SubscriptionManagementSummaryProvider;
import fit.iuh.engolearn.topic.contract.dto.TopicManagementSummaryDto;
import fit.iuh.engolearn.topic.contract.TopicManagementSummaryProvider;
import fit.iuh.engolearn.user.contract.UserManagementSummaryProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardSummaryServiceTest {
    @Test
    void contentManagerReceivesOnlyContentAndDoesNotInvokeAdministrationProviders() {
        var topics = mock(TopicManagementSummaryProvider.class);
        var grammar = mock(GrammarManagementSummaryProvider.class);
        var learningPath = mock(LearningPathManagementSummaryProvider.class);
        var users = mock(UserManagementSummaryProvider.class);
        var subscriptions = mock(SubscriptionManagementSummaryProvider.class);
        var placement = mock(PlacementManagementSummaryProvider.class);
        when(topics.managementSummary()).thenReturn(new TopicManagementSummaryDto(0, 0, 0, 0));
        when(grammar.managementSummary()).thenReturn(new GrammarManagementSummaryDto(0, 0, 0, 0, 0));
        when(learningPath.managementSummary()).thenReturn(new LearningPathManagementSummaryDto(0, 0, 0, 0, 0));
        var service = new DashboardSummaryServiceImpl(topics, grammar, learningPath, users, subscriptions, placement,
                Clock.fixed(Instant.parse("2026-11-28T00:00:00Z"), ZoneOffset.UTC));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("manager", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_CONTENT_MANAGER"))));

        DashboardSummaryResponse response = service.summary();

        assertThat(response.content().topics().total()).isZero();
        assertThat(response.content().grammar().total()).isZero();
        assertThat(response.content().passages().total()).isZero();
        assertThat(response.content().learningPathQuestions().total()).isZero();
        assertThat(response.administration()).isNull();
        verify(users, never()).managementAccountCount();
        verify(subscriptions, never()).countActivePaidPremiumUsersAt(org.mockito.ArgumentMatchers.any());
        verify(placement, never()).managementSummary();
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminGetsAdministrationAndOneInstantIsUsedForPremiumCount() {
        var topics = mock(TopicManagementSummaryProvider.class);
        var grammar = mock(GrammarManagementSummaryProvider.class);
        var learningPath = mock(LearningPathManagementSummaryProvider.class);
        var users = mock(UserManagementSummaryProvider.class);
        var subscriptions = mock(SubscriptionManagementSummaryProvider.class);
        var placement = mock(PlacementManagementSummaryProvider.class);
        var at = Instant.parse("2026-11-28T00:00:00Z");
        when(topics.managementSummary()).thenReturn(new TopicManagementSummaryDto(3, 1, 1, 1));
        when(grammar.managementSummary()).thenReturn(new GrammarManagementSummaryDto(4, 1, 1, 1, 1));
        when(learningPath.managementSummary()).thenReturn(new LearningPathManagementSummaryDto(5, 2, 2, 1, 6));
        when(users.managementAccountCount()).thenReturn(7L);
        when(subscriptions.countActivePaidPremiumUsersAt(at)).thenReturn(8L);
        when(placement.managementSummary()).thenReturn(new fit.iuh.engolearn.placement.contract.dto.PlacementManagementSummaryDto(9, 10));
        var service = new DashboardSummaryServiceImpl(topics, grammar, learningPath, users, subscriptions, placement,
                Clock.fixed(at, ZoneOffset.UTC));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_CONTENT_MANAGER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"))));

        DashboardSummaryResponse response = service.summary();

        assertThat(response.administration().users().total()).isEqualTo(7);
        assertThat(response.administration().subscription().activePremium()).isEqualTo(8);
        assertThat(response.administration().placement().questions()).isEqualTo(10);
        verify(subscriptions).countActivePaidPremiumUsersAt(at);
        SecurityContextHolder.clearContext();
    }

    @Test
    void contentProviderFailuresArePropagatedInsteadOfReturningZeroes() {
        var topics = mock(TopicManagementSummaryProvider.class);
        var grammar = mock(GrammarManagementSummaryProvider.class);
        var learningPath = mock(LearningPathManagementSummaryProvider.class);
        var users = mock(UserManagementSummaryProvider.class);
        var subscriptions = mock(SubscriptionManagementSummaryProvider.class);
        var placement = mock(PlacementManagementSummaryProvider.class);
        when(topics.managementSummary()).thenThrow(new IllegalStateException("topic store unavailable"));
        var service = new DashboardSummaryServiceImpl(topics, grammar, learningPath, users, subscriptions, placement, Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("manager", "",
                List.of(new SimpleGrantedAuthority("ROLE_CONTENT_MANAGER"))));

        assertThatThrownBy(service::summary).isInstanceOf(IllegalStateException.class).hasMessage("topic store unavailable");
        verify(grammar, never()).managementSummary();
        SecurityContextHolder.clearContext();
    }
}
