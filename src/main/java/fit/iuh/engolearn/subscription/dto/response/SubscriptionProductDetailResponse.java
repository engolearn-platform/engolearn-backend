package fit.iuh.engolearn.subscription.dto.response;

import java.util.List;

public record SubscriptionProductDetailResponse(String planCode, String visibility, String saleStatus,
        boolean archived, Long currentVersion, long draftRevision, boolean hasDraft, String displayName,
        SubscriptionProductVersionResponse current, SubscriptionProductDraftResponse draft,
        List<SubscriptionProductVersionSummaryResponse> history, SubscriptionProductVersionResponse publishedVersion,
        LegacyScheduleResponse legacySchedule) {
    public SubscriptionProductDetailResponse { history = List.copyOf(history); }
    public SubscriptionProductDetailResponse(String planCode, String visibility, String saleStatus, boolean archived,
            Long currentVersion, long draftRevision, boolean hasDraft, String displayName,
            SubscriptionProductVersionResponse current, SubscriptionProductDraftResponse draft,
            List<SubscriptionProductVersionSummaryResponse> history, SubscriptionProductVersionResponse publishedVersion) {
        this(planCode, visibility, saleStatus, archived, currentVersion, draftRevision, hasDraft, displayName, current, draft,
                history, publishedVersion, null);
    }
}
