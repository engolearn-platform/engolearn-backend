package fit.iuh.engolearn.quota.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record QuotaUsageItemResponse(String quotaType, String displayName, long used, long limit, long remaining,
        String unit, String period, Instant windowStartAt, Instant windowEndsAt, Instant allowanceStartAt,
        String status, @JsonInclude(JsonInclude.Include.NON_NULL) Long overLimitAmount) {}
