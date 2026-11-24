package fit.iuh.engolearn.subscription.dto.response;

import fit.iuh.engolearn.subscription.dto.request.SubscriptionProductContentRequest;

public record SubscriptionProductDraftResponse(long revision, SubscriptionProductContentRequest content) {}
