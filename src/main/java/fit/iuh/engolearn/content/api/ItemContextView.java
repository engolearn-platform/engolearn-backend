package fit.iuh.engolearn.content.api;

import java.util.List;

public record ItemContextView(
        String prompt,
        String scenarioDescription,
        Integer durationSec,
        String tips,
        List<DialogueTurnView> dialogue) {
}
