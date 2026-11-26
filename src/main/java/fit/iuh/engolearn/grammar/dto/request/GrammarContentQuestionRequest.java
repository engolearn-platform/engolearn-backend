package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GrammarContentQuestionRequest(String id, @NotNull @Pattern(regexp = "MULTIPLE_CHOICE|FILL_IN_BLANK") String type,
                           String prompt, String explanation, @Size(max = 4) List<String> options,
                           Integer correctIndex, String correctAnswer, List<@NotNull String> acceptedAnswers) {
}
