package fit.iuh.engolearn.quota.contract.dto;

import java.time.Instant;

public record AvailabilityResultDto(String quotaType, long used, long limit, long remaining,
        String unit, String period, Instant windowStartAt, Instant windowEndsAt,
        Instant allowanceStartAt, String status) {}
