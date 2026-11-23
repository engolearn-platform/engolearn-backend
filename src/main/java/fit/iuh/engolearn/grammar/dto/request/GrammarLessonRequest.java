package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GrammarLessonRequest(@NotNull @PositiveOrZero Long version, @Positive Integer order,
                              @NotBlank @Size(max = 150) String titleVi,
                              @NotBlank @Size(max = 150) String titleEn,
                              @NotBlank @Size(max = 300) String objective,
                              List<@NotNull String> tags,
                              @Size(max = 7) List<@NotNull @Valid GrammarContentBlockRequest> blocks,
                              @Size(max = 5) List<@NotNull @Valid GrammarContentQuestionRequest> questions) {
}
