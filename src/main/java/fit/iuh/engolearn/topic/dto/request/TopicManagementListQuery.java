package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * E4 list query-param carrier (design record F05 §4.6). The controller binds
 * the raw request params into this record; the service validates it and parses
 * the enum-ish params, so invalid values produce 400 with
 * {@code fieldErrors[].field} = param name.
 */
public record TopicManagementListQuery(

        @Size(max = 200, message = "search must be at most 200 characters")
        String search,

        @Pattern(regexp = "A1|A2|B1|B2", message = "cefr must be one of A1, A2, B1, B2")
        String cefr,

        @Pattern(regexp = "DINING|TRAVEL|BUSINESS|DAILY|HEALTH",
                message = "category must be one of DINING, TRAVEL, BUSINESS, DAILY, HEALTH")
        String category,

        @Pattern(regexp = "draft|published", message = "status must be one of draft, published")
        String status,

        @Min(value = 0, message = "page must be greater than or equal to 0")
        int page,

        @Min(value = 1, message = "size must be between 1 and 100")
        @Max(value = 100, message = "size must be between 1 and 100")
        int size) {
}
