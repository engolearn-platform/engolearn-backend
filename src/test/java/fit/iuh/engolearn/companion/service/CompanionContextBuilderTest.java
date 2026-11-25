package fit.iuh.engolearn.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fit.iuh.engolearn.companion.service.impl.CompanionContextBuilder;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

class CompanionContextBuilderTest {
    private final UserLevelLookup userLevels = mock(UserLevelLookup.class);
    private final CompanionProperties properties = new CompanionProperties();
    private final CompanionContextBuilder builder = new CompanionContextBuilder(userLevels, properties);

    @Test
    void buildsPersonaLevelHistoryAndCurrentInputInChronologicalOrder() {
        when(userLevels.findLevel("learner-1")).thenReturn(Optional.of(CefrLevel.B1));
        List<CompanionHistoryMessage> history = List.of(
                new CompanionHistoryMessage("USER", "Earlier question"),
                new CompanionHistoryMessage("ASSISTANT", "Earlier answer"),
                new CompanionHistoryMessage("USER", "Recent question"));

        Prompt prompt = builder.build("learner-1", history, "Current question");

        List<String> texts = prompt.getInstructions().stream().map(Message::getText).toList();
        assertThat(texts).hasSize(5);
        assertThat(texts.get(0)).contains("EngoLearn's English learning companion", "CEFR level is B1");
        assertThat(texts.subList(1, 4)).containsExactly("Earlier question", "Earlier answer", "Recent question");
        assertThat(texts.get(4)).isEqualTo("Current question");
        assertThat(texts.stream().filter("Current question"::equals)).hasSize(1);
    }

    @Test
    void keepsNewestContiguousHistoryWithinBothConfiguredLimits() {
        properties.setHistoryMessageLimit(2);
        properties.setHistoryTokenBudget(30);
        List<CompanionHistoryMessage> history = List.of(
                new CompanionHistoryMessage("USER", "older"),
                new CompanionHistoryMessage("ASSISTANT", "middle"),
                new CompanionHistoryMessage("USER", "newest"));

        Prompt prompt = builder.build("learner-1", history, "now");

        assertThat(prompt.getInstructions().stream().map(Message::getText).toList())
                .containsSubsequence("middle", "newest", "now")
                .doesNotContain("older");
    }

    @Test
    void stopsAtHistoryTokenBudgetBoundaryAndIncludesLevelAbsenceSafely() {
        properties.setHistoryMessageLimit(10);
        properties.setHistoryTokenBudget(7);
        when(userLevels.findLevel("learner-2")).thenReturn(Optional.empty());
        List<CompanionHistoryMessage> history = List.of(
                new CompanionHistoryMessage("USER", "old"),
                new CompanionHistoryMessage("ASSISTANT", "new"));

        Prompt prompt = builder.build("learner-2", history, "current");

        List<String> texts = prompt.getInstructions().stream().map(Message::getText).toList();
        assertThat(texts.get(0)).contains("The learner's current CEFR level is unavailable. Do not assume or invent it.");
        assertThat(texts).contains("new", "current").doesNotContain("old");
    }
}
