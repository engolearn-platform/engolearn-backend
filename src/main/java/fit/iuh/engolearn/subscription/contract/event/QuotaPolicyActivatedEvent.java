package fit.iuh.engolearn.subscription.contract.event;

import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshotDto;
import java.time.Instant;
import java.util.Set;

public record QuotaPolicyActivatedEvent(String activationId, String policyScope, long policyVersion,
        long activationSequence, Instant effectiveAt, Set<String> changedQuotaTypes,
        QuotaPolicySnapshotDto policySnapshot) {
    public QuotaPolicyActivatedEvent { changedQuotaTypes = Set.copyOf(changedQuotaTypes); }
}
