package fit.iuh.engolearn.grammar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GrammarContentBlockRequest(String id,
                        @NotNull @Pattern(regexp = "EXPLANATION|FORMULA|EXAMPLE|RULE_QUICK_NOTE|COMMON_MISTAKE") String type,
                        String title, String text, String formula,
                        @Size(max = 5) List<@NotNull GrammarContentExampleRequest> examples,
                        @Size(max = 5) List<@NotNull String> ruleItems,
                        String incorrect, String correct, String explanation) {
}
