package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetProductVisibilityRequest(@NotNull @Pattern(regexp = "VISIBLE|HIDDEN") String visibility) {}
