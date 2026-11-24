package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CancelLegacyPremiumScheduleRequest(@NotNull Long expectedVersion,
        @NotNull Instant expectedEffectiveAt) {}
