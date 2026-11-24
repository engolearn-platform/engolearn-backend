package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleLegacyPremiumRequest(@NotNull Long expectedVersion,
        @NotNull Instant expectedEffectiveAt, @NotNull Instant effectiveAt) {}
