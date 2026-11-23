package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record GrammarTopicRequest(@PositiveOrZero Long version, @Positive Integer order,
                             @NotBlank @Size(max = 120) String titleVi,
                             @NotBlank @Size(max = 120) String titleEn,
                             @NotBlank @Size(max = 250) String description,
                             @NotNull @Pattern(regexp = "A1|A2|B1|B2") String level) {
}
