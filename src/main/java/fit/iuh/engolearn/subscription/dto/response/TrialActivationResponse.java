package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record TrialActivationResponse(String trialPeriodId, Instant periodStartAt, Instant periodEndAt, String policySnapshotId) {}
