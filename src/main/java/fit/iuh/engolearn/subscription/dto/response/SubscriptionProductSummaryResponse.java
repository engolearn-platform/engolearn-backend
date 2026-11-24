package fit.iuh.engolearn.subscription.dto.response;

public record SubscriptionProductSummaryResponse(String planCode, String visibility, String saleStatus,
        boolean archived, Long currentVersion, long draftRevision, boolean hasDraft, String displayName) {}
