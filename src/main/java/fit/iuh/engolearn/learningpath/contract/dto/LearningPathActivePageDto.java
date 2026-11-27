package fit.iuh.engolearn.learningpath.contract.dto;

import fit.iuh.engolearn.learningpath.contract.enums.*;
import java.time.Instant;
import java.util.List;

public record LearningPathActivePageDto(List<Row> items, boolean hasMore, String nextCursor,
                                       LearningPathCoverage coverage, Instant asOf) {
    public LearningPathActivePageDto { items = List.copyOf(items); }

    public record Row(String pathId, String cefrLevel, String status, Integer currentWeekNo,
                      List<String> microSkills, Integer assigned, Integer answered, Integer correct,
                      LearningPathAvailability availability, List<NextAction> nextActions) {
        public Row {
            microSkills = List.copyOf(microSkills);
            nextActions = List.copyOf(nextActions);
        }
    }

    public record NextAction(int weekNo, int dayNo, String dayStatus, LearningPathAction action,
                             Instant startedAt, Instant autoCloseAt,
                             LearningPathAvailability availability, boolean requiresAppRecheck) {}
}
