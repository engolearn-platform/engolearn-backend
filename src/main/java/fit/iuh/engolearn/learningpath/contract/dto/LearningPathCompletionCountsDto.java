package fit.iuh.engolearn.learningpath.contract.dto;

import fit.iuh.engolearn.learningpath.contract.enums.LearningPathCoverage;
import java.time.Instant;

public record LearningPathCompletionCountsDto(Instant asOf, LearningPathCoverage coverage,
                                               long completedPaths, String scope) {}
