package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrammarSubmitAnswerRequest(@NotBlank @Size(max = 128) String requestId,
            @NotBlank @Size(max = 128) String questionId,
            @NotBlank String answer) {
}
