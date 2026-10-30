package fit.iuh.engolearn.subscription.dto.request;

import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshotDto;
import fit.iuh.engolearn.subscription.dto.response.CurrentBenefitsResponse;
import fit.iuh.engolearn.subscription.dto.response.PlanCardResponse;
import java.time.Instant;
import java.util.List;

public record PublicationManifestRequest(String manifestId, String environment, List<PlanRequest> plans,
    List<PolicyRequest> policies, List<AccountHistoryRequest> accounts) {
    public record PlanRequest(String id, String scope, long version, Instant effectiveAt, boolean published,
        int displayOrder, PlanCardResponse plan) {}
    public record PolicyRequest(String id, String scope, long version, Instant effectiveAt,
        QuotaPolicySnapshotDto snapshot, CurrentBenefitsResponse benefits) {}
    public record AccountHistoryRequest(String userId, boolean trialUsed, String provenance) {}
}
