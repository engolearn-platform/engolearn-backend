package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record MySubscriptionSummaryResponse(String tier, String status, Instant periodStartAt, Instant periodEndAt,
    boolean trialEligible, String trialIneligibilityReason, CurrentBenefitsResponse currentBenefits) {}
