package fit.iuh.engolearn.managementdashboard.service.impl;

import fit.iuh.engolearn.grammar.contract.GrammarManagementSummaryProvider;
import fit.iuh.engolearn.grammar.contract.dto.GrammarManagementSummaryDto;
import fit.iuh.engolearn.learningpath.contract.LearningPathManagementSummaryProvider;
import fit.iuh.engolearn.learningpath.contract.dto.LearningPathManagementSummaryDto;
import fit.iuh.engolearn.managementdashboard.dto.response.DashboardSummaryResponse;
import fit.iuh.engolearn.managementdashboard.service.DashboardSummaryService;
import fit.iuh.engolearn.placement.contract.PlacementManagementSummaryProvider;
import fit.iuh.engolearn.placement.contract.dto.PlacementManagementSummaryDto;
import fit.iuh.engolearn.subscription.contract.SubscriptionManagementSummaryProvider;
import fit.iuh.engolearn.topic.contract.TopicManagementSummaryProvider;
import fit.iuh.engolearn.topic.contract.dto.TopicManagementSummaryDto;
import fit.iuh.engolearn.user.contract.UserManagementSummaryProvider;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardSummaryServiceImpl implements DashboardSummaryService {
    private final TopicManagementSummaryProvider topics;
    private final GrammarManagementSummaryProvider grammar;
    private final LearningPathManagementSummaryProvider learningPath;
    private final UserManagementSummaryProvider users;
    private final SubscriptionManagementSummaryProvider subscriptions;
    private final PlacementManagementSummaryProvider placement;
    private final Clock clock;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public DashboardSummaryResponse summary() {
        var topicCounts = topics.managementSummary();
        var grammarCounts = grammar.managementSummary();
        var learningPathCounts = learningPath.managementSummary();
        var content = new DashboardSummaryResponse.Content(
                new DashboardSummaryResponse.Topics(topicCounts.total(), topicCounts.published(), topicCounts.draft(), topicCounts.unpublished()),
                new DashboardSummaryResponse.Grammar(grammarCounts.total(), grammarCounts.published(), grammarCounts.draft(),
                        grammarCounts.inProgress(), grammarCounts.archived()),
                new DashboardSummaryResponse.Passages(learningPathCounts.passageTotal(), learningPathCounts.passageActive(),
                        learningPathCounts.passageDraft(), learningPathCounts.passageRetired()),
                new DashboardSummaryResponse.LearningPathQuestions(learningPathCounts.questionTotal()));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin) return new DashboardSummaryResponse(content, null);

        var placementCounts = placement.managementSummary();
        var administration = new DashboardSummaryResponse.Administration(
                new DashboardSummaryResponse.Users(users.managementAccountCount()),
                new DashboardSummaryResponse.Subscription(subscriptions.countActivePaidPremiumUsersAt(clock.instant())),
                new DashboardSummaryResponse.Placement(placementCounts.testSets(), placementCounts.questions()));
        return new DashboardSummaryResponse(content, administration);
    }
}
