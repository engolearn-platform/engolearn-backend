package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record MySubscriptionSummary(String tier, String status, Instant periodStartAt, Instant periodEndAt,
    boolean trialEligible, String trialIneligibilityReason, CurrentBenefits currentBenefits) {}
