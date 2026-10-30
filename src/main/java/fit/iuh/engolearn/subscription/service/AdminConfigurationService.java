package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse;
import fit.iuh.engolearn.subscription.dto.response.AdminConfigurationResponse.*;

public interface AdminConfigurationService {
    ConfigurationCatalogResponse catalog();
    AdminConfigurationResponse configuration(String scope);
    ConfigurationHistoryResponse history(String scope, int limit, Long before);
    ConfigurationVersionResponse detail(String scope, long number);
    AdminPublicationRequest normalize(AdminPublicationRequest input);
    ConfigurationPreviewResponse preview(String scope, AdminPublicationRequest input);
    Accepted publish(String actor, String key, String scope, AdminPublicationRequest input);
    record Accepted(PublicationResultResponse result, boolean created) {}
}
