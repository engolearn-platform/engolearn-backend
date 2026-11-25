package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.service.CompanionHistoryMessage;
import fit.iuh.engolearn.companion.service.CompanionPromptMessage;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CompanionContextBuilder {
    private static final String ASSESSMENT_CONTEXT_INSTRUCTION = "When the learner asks about their latest assessment, "
            + "use getLatestAssessmentResult before answering whenever that tool is available, and request it before "
            + "sending any answer text. For skill advice, request mode SKILLS without skillCodes unless an exact "
            + "skill code was returned earlier in this turn. Ground explanations only in returned visible fields and "
            + "skills. For PREVIEW results, discuss only returned rows and do not rank or compare unreturned skills. "
            + "Never guess hidden results or treat one result as a trend. If no result or usable evidence is returned, "
            + "acknowledge that plainly and ask what help the learner wants.";

    private final UserLevelLookup userLevels;
    private final CompanionProperties properties;

    public CompanionContextBuilder(UserLevelLookup userLevels, CompanionProperties properties) {
        this.userLevels = userLevels;
        this.properties = properties;
    }

    public List<CompanionPromptMessage> build(String learnerId, List<CompanionHistoryMessage> history, String currentInput) {
        List<CompanionPromptMessage> messages = new ArrayList<>();
        String levelContext = userLevels.findLevel(learnerId)
                .map(level -> "The learner's current CEFR level is " + level.name() + ". Adapt vocabulary and explanations to this level.")
                .orElse("The learner's current CEFR level is unavailable. Do not assume or invent it.");
        messages.add(new CompanionPromptMessage(CompanionPromptMessage.Role.SYSTEM,
                loadPersona() + "\n\n" + levelContext + "\n\n" + ASSESSMENT_CONTEXT_INSTRUCTION));
        for (CompanionHistoryMessage prior : selectHistory(history)) {
            messages.add(toMessage(prior));
        }
        messages.add(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, currentInput));
        return List.copyOf(messages);
    }

    private List<CompanionHistoryMessage> selectHistory(List<CompanionHistoryMessage> history) {
        if (history == null || history.isEmpty() || properties.getHistoryMessageLimit() <= 0
                || properties.getHistoryTokenBudget() <= 0) {
            return List.of();
        }
        List<CompanionHistoryMessage> selectedNewestFirst = new ArrayList<>();
        long usedTokens = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            CompanionHistoryMessage message = history.get(index);
            int messageTokens = estimateUpperBoundTokens(message.content());
            if (selectedNewestFirst.size() >= properties.getHistoryMessageLimit()
                    || usedTokens + messageTokens > properties.getHistoryTokenBudget()) {
                break;
            }
            selectedNewestFirst.add(message);
            usedTokens += messageTokens;
        }
        Collections.reverse(selectedNewestFirst);
        return selectedNewestFirst;
    }

    private int estimateUpperBoundTokens(String content) {
        return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length + 4;
    }

    private CompanionPromptMessage toMessage(CompanionHistoryMessage message) {
        return switch (message.role()) {
            case "USER" -> new CompanionPromptMessage(CompanionPromptMessage.Role.USER, message.content());
            case "ASSISTANT" -> new CompanionPromptMessage(CompanionPromptMessage.Role.ASSISTANT, message.content());
            default -> throw new IllegalArgumentException("Unsupported companion history role");
        };
    }

    private String loadPersona() {
        String version = properties.getPersonaVersion();
        if (version == null || !version.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("Invalid companion persona version");
        }
        try (var input = new ClassPathResource("companion/persona-" + version + ".md").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Companion persona resource is unavailable", exception);
        }
    }
}
