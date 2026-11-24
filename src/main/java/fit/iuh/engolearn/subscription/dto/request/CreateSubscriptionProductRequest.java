package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSubscriptionProductRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{0,63}") String planCode) {}
