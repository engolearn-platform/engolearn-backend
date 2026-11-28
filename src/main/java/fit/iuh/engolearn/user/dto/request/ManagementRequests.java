package fit.iuh.engolearn.user.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.enums.ManagementStatus;
import jakarta.validation.constraints.*;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ManagementRequests {
    private ManagementRequests() {}

    private static void fields(JsonNode json, String... fields) {
        if (json == null || !json.isObject() || !Set.of(fields).containsAll(json.propertyNames()))
            throw new IllegalArgumentException("Invalid fields");
    }
    private static String string(JsonNode json, String key) {
        if (!json.path(key).isString()) throw new IllegalArgumentException("Invalid field type");
        return json.get(key).asString();
    }
    private static long revision(JsonNode json) {
        var field = json.path("expectedRevision");
        if (!field.isIntegralNumber() || !field.canConvertToLong() || field.longValue() < 0)
            throw new IllegalArgumentException("Invalid revision");
        return field.longValue();
    }
    private static String name(JsonNode json) { return string(json, "name").strip(); }

    public record CreateRequest(@NotBlank @Size(max=80) String name, @Email @NotBlank @Size(max=254) String email,
                                @NotBlank @Size(min=8,max=72) String initialPassword, @NotNull UserRole role) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public static CreateRequest read(JsonNode json) {
            fields(json, "name", "email", "initialPassword", "role");
            return new CreateRequest(ManagementRequests.name(json), string(json, "email").trim(), string(json, "initialPassword"),
                    json.has("role") ? UserRole.valueOf(string(json, "role")) : UserRole.USER);
        }
        @Override public String toString() { return "CreateRequest"; }
    }
    public record EditRequest(@NotBlank @Size(max=80) String name, @PositiveOrZero long expectedRevision, String expectedName) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public static EditRequest read(JsonNode json) {
            fields(json, "name", "expectedRevision", "expectedName");
            return new EditRequest(ManagementRequests.name(json), revision(json), json.path("expectedName").isNull() ? null : string(json, "expectedName"));
        }
    }
    public record RoleRequest(@NotNull UserRole role, @PositiveOrZero long expectedRevision) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public static RoleRequest read(JsonNode json) {
            fields(json, "role", "expectedRevision");
            return new RoleRequest(UserRole.valueOf(string(json,"role")), revision(json));
        }
    }
    public record StatusRequest(@NotNull ManagementStatus status, @PositiveOrZero long expectedRevision) {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public static StatusRequest read(JsonNode json) {
            fields(json, "status", "expectedRevision");
            return new StatusRequest(ManagementStatus.valueOf(string(json,"status")), revision(json));
        }
    }
    public record ListRequest(String search, UserRole role, ManagementStatus status, String sort, int page, int size) {
        public static ListRequest fromQuery(String search, String role, String status, String sort, String page, String size) {
            return new ListRequest(search.strip(), role == null ? null : UserRole.valueOf(role),
                    status == null ? null : ManagementStatus.valueOf(status), sort, Integer.parseInt(page), Integer.parseInt(size));
        }
    }
}
