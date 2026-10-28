package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AnswerQuizRequest(@NotNull @PositiveOrZero Long revision, @NotNull @NotBlank String optionId) {}
