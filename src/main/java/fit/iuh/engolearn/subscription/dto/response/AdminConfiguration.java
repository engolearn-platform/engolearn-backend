package fit.iuh.engolearn.subscription.dto.response;

import fit.iuh.engolearn.subscription.dto.request.AdminPublicationInput.Content;
import java.time.Instant;
import java.util.List;

public record AdminConfiguration(long revision, Instant serverTime, String scope, Version current,
    Version pending, boolean activationPending) {
    public record Version(String scope,long version,Instant effectiveAt,String status,Content content) {}
    public record Catalog(long revision,Instant serverTime,String timeZone,List<QuotaType> quotaTypes,List<String> scopes) {}
    public record QuotaType(String quotaType,String unit,String period) {}
    public record History(List<Version> items,Long nextBeforeVersion) {}
    public record Preview(long revision,String scope,Instant effectiveAt,List<String> changedQuotaTypes,String impact,Content content) {}
    public record PublicationResult(String publicationId,String scope,long version,Instant effectiveAt,Instant acceptedAt,long revision) {}
}
