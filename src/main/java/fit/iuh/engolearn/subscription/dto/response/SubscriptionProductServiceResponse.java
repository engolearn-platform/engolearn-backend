package fit.iuh.engolearn.subscription.dto.response;

public record SubscriptionProductServiceResponse(String code, String title, String description,
        String quotaType, String unit, String period) {}
