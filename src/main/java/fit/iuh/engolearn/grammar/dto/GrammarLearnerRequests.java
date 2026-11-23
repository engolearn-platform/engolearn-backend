package fit.iuh.engolearn.grammar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class GrammarLearnerRequests {
    private GrammarLearnerRequests() {
    }

    public record SeenRequest(@Positive int contentVersion) { }

    public record StartPracticeRequest(Boolean restart) { }

    public record SubmitAnswerRequest(
            @NotBlank @Size(max = 128) String requestId,
            @NotBlank @Size(max = 128) String questionId,
            @NotBlank String answer) { }
}
