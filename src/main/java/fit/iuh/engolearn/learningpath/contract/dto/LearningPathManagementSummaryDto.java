package fit.iuh.engolearn.learningpath.contract.dto;

public record LearningPathManagementSummaryDto(long passageTotal, long passageActive, long passageDraft,
                                                long passageRetired, long questionTotal) { }
