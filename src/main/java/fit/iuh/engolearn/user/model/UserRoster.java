package fit.iuh.engolearn.user.model;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.enums.ManagementStatus;
import java.time.Instant;
import java.util.List;

public final class UserRoster {
    private UserRoster() {}

    public record Filter(String search, UserRole role, ManagementStatus status, String sort, int page, int size) {}
    public record Entry(String id, String name, String email, UserRole role, ManagementStatus status,
                        Instant createdAt, long revision) {}
    public record Page(List<Entry> items, long total) {}
}
