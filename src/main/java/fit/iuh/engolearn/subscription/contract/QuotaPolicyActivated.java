package fit.iuh.engolearn.subscription.contract;

import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshot;
import java.time.Instant;
import java.util.Set;

public record QuotaPolicyActivated(String activationId, String policyScope, long policyVersion,
        long activationSequence, Instant effectiveAt, Set<String> changedQuotaTypes,
        QuotaPolicySnapshot policySnapshot) {
    public QuotaPolicyActivated { changedQuotaTypes = Set.copyOf(changedQuotaTypes); }
}
