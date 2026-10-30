package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;

public interface SubscriptionPublicationService {
    void publish(PublicationManifestRequest manifest);
}
