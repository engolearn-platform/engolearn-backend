package fit.iuh.engolearn.quota.dto.response;

import java.util.List;

public record QuotaUsageSummaryResponse(List<QuotaUsageItemResponse> items) {
    public QuotaUsageSummaryResponse { items = List.copyOf(items); }
}
