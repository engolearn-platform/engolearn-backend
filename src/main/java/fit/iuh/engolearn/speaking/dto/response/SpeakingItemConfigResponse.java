package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.speaking.model.SpeakingGoal;

import java.util.List;

public record SpeakingItemConfigResponse(String itemId, String itemVersionId, String title,
                                         List<SpeakingGoal> goals, int goalPercentage,
                                         int requiredGoalCount, int minimumValidTurns) {}
