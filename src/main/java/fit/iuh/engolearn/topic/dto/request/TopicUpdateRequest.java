package fit.iuh.engolearn.topic.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * E3 partial-update request (design record F05 §4.4) — all fields optional with
 * the frozen merge matrix: <em>absent</em> = keep, JSON <em>null</em> = clear
 * (optional fields only), present non-null = replace; blank/invalid = 400.
 *
 * <p>Because absent and explicit-{@code null} must be distinguishable, this
 * record is bound by the nested {@link Deserializer}, which also rejects
 * unknown body fields ({@code DEC-21}) and records which keys were present in
 * {@link #providedFields()}.</p>
 *
 * <p>Deliberately no {@code @NotBlank} here: {@code @NotBlank} also rejects
 * {@code null}, which is legal on PATCH (absent key or explicit null). The
 * "names can never be cleared/blanked" rule is enforced in the service.</p>
 */
@JsonDeserialize(using = TopicUpdateRequest.Deserializer.class)
public record TopicUpdateRequest(

        @Size(max = 200, message = "titleEn must be at most 200 characters")
        String titleEn,

        @Size(max = 300, message = "titleVi must be at most 300 characters")
        String titleVi,

        @Pattern(regexp = "A1|A2|B1|B2", message = "cefr must be one of A1, A2, B1, B2")
        String cefr,

        @Pattern(regexp = "DINING|TRAVEL|BUSINESS|DAILY|HEALTH",
                message = "category must be one of DINING, TRAVEL, BUSINESS, DAILY, HEALTH")
        String category,

        @Size(max = 250, message = "description must be at most 250 characters")
        String description,

        @Size(max = 500, message = "coverUrl must be at most 500 characters")
        String coverUrl,

        /** Names of the JSON keys actually present in the request body (internal, not wire-facing). */
        @JsonIgnore
        Set<String> providedFields) {

    public static final String TITLE_EN = "titleEn";
    public static final String TITLE_VI = "titleVi";
    public static final String CEFR = "cefr";
    public static final String CATEGORY = "category";
    public static final String DESCRIPTION = "description";
    public static final String COVER_URL = "coverUrl";

    public TopicUpdateRequest {
        providedFields = providedFields == null ? Set.of() : Set.copyOf(providedFields);
    }

    /** {@code true} when the request body contained this key (even with a {@code null} value). */
    public boolean isProvided(String field) {
        return providedFields.contains(field);
    }

    /**
     * Binds the raw JSON object: rejects unknown keys and non-string values,
     * and tracks key presence so the service can distinguish
     * absent (keep) from explicit {@code null} (clear).
     */
    public static final class Deserializer extends ValueDeserializer<TopicUpdateRequest> {

        private static final Set<String> KNOWN_FIELDS =
                Set.of(TITLE_EN, TITLE_VI, CEFR, CATEGORY, DESCRIPTION, COVER_URL);

        @Override
        public TopicUpdateRequest deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            JsonNode body = context.readTree(parser);
            if (body == null || body.isNull()) {
                return null;
            }
            if (!body.isObject()) {
                throw DatabindException.from(context, "Request body must be a JSON object");
            }

            Set<String> provided = new HashSet<>();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonNode> entry : body.properties()) {
                String name = entry.getKey();
                if (!KNOWN_FIELDS.contains(name)) {
                    throw DatabindException.from(context, "Unrecognized field \"" + name + "\"");
                }
                provided.add(name);
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                if (!value.isTextual()) {
                    throw DatabindException.from(context, "Field \"" + name + "\" must be a string or null");
                }
                values.put(name, value.stringValue());
            }

            return new TopicUpdateRequest(
                    values.get(TITLE_EN),
                    values.get(TITLE_VI),
                    values.get(CEFR),
                    values.get(CATEGORY),
                    values.get(DESCRIPTION),
                    values.get(COVER_URL),
                    Set.copyOf(provided));
        }
    }
}
