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

        List<CompanionPromptMessage> prompt = builder.build("learner-1", history, "Current question");

        List<String> texts = prompt.stream().map(CompanionPromptMessage::text).toList();
        assertThat(texts).hasSize(5);
        assertThat(prompt.stream().map(CompanionPromptMessage::role).toList()).containsExactly(
                CompanionPromptMessage.Role.SYSTEM, CompanionPromptMessage.Role.USER,
                CompanionPromptMessage.Role.ASSISTANT, CompanionPromptMessage.Role.USER,
                CompanionPromptMessage.Role.USER);
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

        List<CompanionPromptMessage> prompt = builder.build("learner-1", history, "now");

        assertThat(prompt.stream().map(CompanionPromptMessage::text).toList())
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

        List<CompanionPromptMessage> prompt = builder.build("learner-2", history, "current");

        List<String> texts = prompt.stream().map(CompanionPromptMessage::text).toList();
        assertThat(texts.get(0)).contains("The learner's current CEFR level is unavailable. Do not assume or invent it.");
        assertThat(texts).contains("new", "current").doesNotContain("old");
    }

    @Test
    void historyComparabilityIsScopedAndLatestVisibleSkillGuidanceRemains() {
        String prompt = builder.build("learner-1",List.of(),"Guide me").getFirst().text();
        assertThat(prompt).contains("For dated assessment history comparisons, use only returned visible comparable observations",
                "For skill advice, request mode SKILLS without skillCodes",
                "For PREVIEW results, discuss only returned rows and do not rank or compare unreturned skills",
                "Never guess hidden results or treat one result as a trend",
                "get both Topic and Learning Path COUNTS unless already known",
                "completed topics, topic items and completed paths separately",
                "request both Topic RESUME and Path ACTIVE",
                "use Path RECENT and explicitly disclose that dated Topic activity and other modules are not covered",
                "request Assessment history SKILLS",
                "Do not repeat the same tool and arguments after NO_DATA or ERROR");
    }

    @Test
    void directMemoryRequestsRemainAvailableAndSuccessMustBeConfirmedWhenAutomaticMemoryIsOff() {
        List<CompanionPromptMessage> prompt = builder.build("learner-1", List.of(),
                "Please remember my coffee preference.", null, false);

        String system = prompt.get(0).text();
        assertThat(system).contains("including when Automatic Memory is OFF",
                "set trigger USER_REQUESTED only when", "otherwise use AUTOMATIC",
                "retaining relevant negations and qualifiers", "Never automatically forget a memory",
                "Claim a memory change succeeded only when its tool result confirms status SUCCESS",
                "Automatic Memory is OFF. Do not opportunistically create or update memories",
                "direct user requests to remember, update, or forget remain available through the memory tools");
    }
}
