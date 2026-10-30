package fit.iuh.engolearn.quota.contract.dto;

import java.time.Instant;

public record UsageResultDto(boolean accepted, String ledgerId, long amount, Instant recordedAt,
        String allowanceId, AvailabilityResultDto availability) {}
