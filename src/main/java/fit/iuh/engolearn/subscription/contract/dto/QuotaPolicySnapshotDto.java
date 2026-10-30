package fit.iuh.engolearn.subscription.contract.dto;

import java.util.List;

public record QuotaPolicySnapshotDto(String policySnapshotId, String policyScope, long policyVersion, List<QuotaRuleDto> rules) {
    public QuotaPolicySnapshotDto { rules = List.copyOf(rules); }
}
