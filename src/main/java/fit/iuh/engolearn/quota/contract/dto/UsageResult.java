package fit.iuh.engolearn.quota.contract.dto;

import java.time.Instant;

public record UsageResult(boolean accepted, String ledgerId, long amount, Instant recordedAt,
        String allowanceId, AvailabilityResult availability) {}
