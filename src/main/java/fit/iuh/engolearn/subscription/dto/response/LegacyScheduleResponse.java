package fit.iuh.engolearn.subscription.dto.response;

import java.time.Instant;

public record LegacyScheduleResponse(long version, Instant effectiveAt, String status) {}
