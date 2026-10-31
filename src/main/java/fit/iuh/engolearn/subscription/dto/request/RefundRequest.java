package fit.iuh.engolearn.subscription.dto.request;
import jakarta.validation.constraints.*;
public record RefundRequest(@NotBlank @Size(max=1000) String reason) {}
