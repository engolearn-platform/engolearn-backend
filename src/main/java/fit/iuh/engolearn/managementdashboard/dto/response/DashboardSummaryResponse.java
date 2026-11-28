package fit.iuh.engolearn.managementdashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardSummaryResponse(Content content, Administration administration) {
    public record Content(Topics topics, Grammar grammar, Passages passages, LearningPathQuestions learningPathQuestions) { }
    public record Topics(long total, long published, long draft, long unpublished) { }
    public record Grammar(long total, long published, long draft, long inProgress, long archived) { }
    public record Passages(long total, long active, long draft, long retired) { }
    public record LearningPathQuestions(long total) { }
    public record Administration(Users users, Subscription subscription, Placement placement) { }
    public record Users(long total) { }
    public record Subscription(long activePremium) { }
    public record Placement(long testSets, long questions) { }
}
