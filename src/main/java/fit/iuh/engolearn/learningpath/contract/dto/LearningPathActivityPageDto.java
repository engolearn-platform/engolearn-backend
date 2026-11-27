package fit.iuh.engolearn.learningpath.contract.dto;

import fit.iuh.engolearn.learningpath.contract.enums.*;
import java.time.Instant;
import java.util.List;

public record LearningPathActivityPageDto(List<Row> items, boolean hasMore, String nextCursor,
                                         LearningPathCoverage coverage, Instant asOf) {
    public LearningPathActivityPageDto { items = List.copyOf(items); }

    public record Row(String pathId, int weekNo, int dayNo, String dayStatus,
                      LearningPathEventKind eventKind, Instant occurredAt, String closeReason,
                      Double completionRate, Double practiceScore) {}
}
