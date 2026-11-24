package fit.iuh.engolearn.subscription.dto.response;

import java.util.List;

public record SubscriptionProductAdminListResponse(List<SubscriptionProductSummaryResponse> items,
        List<SubscriptionProductServiceResponse> services) {
    public SubscriptionProductAdminListResponse {
        items = List.copyOf(items);
        services = List.copyOf(services);
    }
}
