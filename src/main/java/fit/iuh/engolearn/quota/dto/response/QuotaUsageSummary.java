package fit.iuh.engolearn.quota.dto.response;

import java.util.List;

public record QuotaUsageSummary(List<QuotaUsageItem> items) {
    public QuotaUsageSummary { items = List.copyOf(items); }
}
