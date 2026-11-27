package fit.iuh.engolearn.learningpath.contract;

import fit.iuh.engolearn.learningpath.contract.dto.*;
import java.time.Instant;

/** Owner-scoped, read-only projections for authenticated Companion turns. */
public interface LearningPathCompanionReads {
    LearningPathActivePageDto active(String learnerId, Instant asOf, LearningPathPageQueryDto query);
    LearningPathCompletionCountsDto counts(String learnerId);
    LearningPathActivityPageDto recent(String learnerId, Instant fromInclusive, Instant toExclusive,
                                       LearningPathPageQueryDto query);
}
