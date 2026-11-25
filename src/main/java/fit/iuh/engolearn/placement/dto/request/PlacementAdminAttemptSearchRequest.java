package fit.iuh.engolearn.placement.dto.request;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record PlacementAdminAttemptSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 128) String search,
        @Size(max = 128) String userId,
        @Size(max = 128) String testSetCode,
        @Min(1) Integer version,
        PlacementAttemptStatus status,
        PlacementLevel level,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
