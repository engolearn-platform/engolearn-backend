package fit.iuh.engolearn.assessment.contract.dto;

public record AssessmentCompanionHistoryRowDto(AssessmentCompanionSummaryDto result,
        String paperId, int paperVersion, int rulesVersion, boolean demonstration) {}