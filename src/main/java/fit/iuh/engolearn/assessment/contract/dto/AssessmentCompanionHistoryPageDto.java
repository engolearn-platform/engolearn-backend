package fit.iuh.engolearn.assessment.contract.dto;

import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionHistoryCoverage;
import java.time.Instant;
import java.util.List;

public record AssessmentCompanionHistoryPageDto(List<AssessmentCompanionHistoryRowDto> items,
        boolean hasMore, String nextCursor, AssessmentCompanionHistoryCoverage coverage, Instant asOf) {
    public AssessmentCompanionHistoryPageDto {
        items = List.copyOf(items);
    }
}