package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;

import java.util.List;

public record SpeakingItemConfigResponse(String itemId, String itemVersionId, String title,
                                         List<SpeakingGoal> goals, int minimumValidTurns,
                                         String context, String learnerRole, String aiRole,
                                         SpeakingItemConfig.OpeningSide openingSide) {
    public SpeakingItemConfigResponse(String itemId, String itemVersionId, String title,
            List<SpeakingGoal> goals, int minimumValidTurns) {
        this(itemId, itemVersionId, title, goals, minimumValidTurns, null, null, null, null);
    }
}
