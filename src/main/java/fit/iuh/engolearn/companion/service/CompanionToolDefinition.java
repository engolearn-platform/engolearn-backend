package fit.iuh.engolearn.companion.service;

/** Provider-neutral declaration for a tool the application may choose to execute. */
public record CompanionToolDefinition(String name, String description, String inputSchemaJson) {
    public CompanionToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description is required");
        }
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            throw new IllegalArgumentException("Tool input schema is required");
        }
    }

    @Override
    public String toString() {
        return "CompanionToolDefinition[name=" + name + ", description=redacted, inputSchemaJson=redacted]";
    }
}
