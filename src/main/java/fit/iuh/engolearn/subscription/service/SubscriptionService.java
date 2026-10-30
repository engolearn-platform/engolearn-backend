package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.PolicyVersion;
import java.time.Instant;
import java.util.List;

public interface SubscriptionService {
    List<PlanCardResponse> plans();
    MySubscriptionSummaryResponse summary(String userId);
    PolicyVersion policy(String scope, Instant at);
    ActivationResult activateTrial(String userId, String key);
    record ActivationResult(TrialActivationResponse activation, boolean created) {}
}
