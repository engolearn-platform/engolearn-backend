package fit.iuh.engolearn.user.dto.response;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.enums.ManagementStatus;
import java.time.Instant;
import java.util.List;

public final class ManagementResponses {
    private ManagementResponses() {}
    public record SummaryResponse(String id, String name, String email, UserRole role, ManagementStatus status,
                                  Instant createdAt) {}
    public record DetailResponse(String id, String name, String email, UserRole role, ManagementStatus status,
                                 Instant createdAt, long revision) {}
    public record ListResponse(List<SummaryResponse> items, int page, int size, long totalElements, long totalPages) {
        public ListResponse { items = List.copyOf(items); }
    }
    public record DeletedResponse(String id) {}
}
