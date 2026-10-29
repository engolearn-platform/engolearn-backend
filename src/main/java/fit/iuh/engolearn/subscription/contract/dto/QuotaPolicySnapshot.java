package fit.iuh.engolearn.subscription.contract.dto;

import java.util.List;

public record QuotaPolicySnapshot(String policySnapshotId, String policyScope, long policyVersion, List<QuotaRule> rules) {
    public QuotaPolicySnapshot { rules = List.copyOf(rules); }
}
