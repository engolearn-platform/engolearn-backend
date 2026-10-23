package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * E1 create request (design record F05 §4.2). Draft-permissive: only the two
 * names are mandatory ({@code INV-06}/{@code INV-07}); every other field is
 * optional. No duration/tags/status field exists — unknown body fields are
 * rejected with 400 ({@code DEC-21}).
 */
public record TopicCreateRequest(

        @NotBlank(message = "titleEn must not be blank")
        @Size(max = 200, message = "titleEn must be at most 200 characters")
        String titleEn,

        @NotBlank(message = "titleVi must not be blank")
        @Size(max = 300, message = "titleVi must be at most 300 characters")
        String titleVi,

        @Pattern(regexp = "A1|A2|B1|B2", message = "cefr must be one of A1, A2, B1, B2")
        String cefr,

        @Pattern(regexp = "DINING|TRAVEL|BUSINESS|DAILY|HEALTH",
                message = "category must be one of DINING, TRAVEL, BUSINESS, DAILY, HEALTH")
        String category,

        @Size(max = 250, message = "description must be at most 250 characters")
        String description,

        // Optional: absent/null is valid; "non-blank when present" is enforced in the service
        // (Bean Validation cannot express "not blank if not null" without a custom constraint).
        @Size(max = 500, message = "coverUrl must be at most 500 characters")
        String coverUrl) {
}
