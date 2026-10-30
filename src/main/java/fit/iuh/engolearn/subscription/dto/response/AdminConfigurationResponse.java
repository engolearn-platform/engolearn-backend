package fit.iuh.engolearn.subscription.dto.response;

import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.ConfigurationContentRequest;
import java.time.Instant;
import java.util.List;

public record AdminConfigurationResponse(long revision, Instant serverTime, String scope, ConfigurationVersionResponse current,
    ConfigurationVersionResponse pending, boolean activationPending) {
    public record ConfigurationVersionResponse(String scope,long version,Instant effectiveAt,String status,ConfigurationContentRequest content) {}
    public record ConfigurationCatalogResponse(long revision,Instant serverTime,String timeZone,List<QuotaTypeResponse> quotaTypes,List<String> scopes) {}
    public record QuotaTypeResponse(String quotaType,String unit,String period) {}
    public record ConfigurationHistoryResponse(List<ConfigurationVersionResponse> items,Long nextBeforeVersion) {}
    public record ConfigurationPreviewResponse(long revision,String scope,Instant effectiveAt,List<String> changedQuotaTypes,String impact,ConfigurationContentRequest content) {}
    public record PublicationResultResponse(String publicationId,String scope,long version,Instant effectiveAt,Instant acceptedAt,long revision) {}
}
