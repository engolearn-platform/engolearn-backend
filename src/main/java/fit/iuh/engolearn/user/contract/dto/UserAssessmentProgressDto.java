package fit.iuh.engolearn.user.contract.dto;

import java.time.Instant;

/** Placement starting level and periodic Assessment progress, independent of the learning unlock level. */
public record UserAssessmentProgressDto(
        String initialLevel,
        String currentLevel,
        String highestLevel,
        Instant latestValidAt,
        String latestValidAttemptId,
        boolean developmentData
) { }
