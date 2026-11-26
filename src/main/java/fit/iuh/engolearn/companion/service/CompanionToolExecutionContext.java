package fit.iuh.engolearn.companion.service;

public record CompanionToolExecutionContext(String learnerId, Runnable onAssessmentLookup,
        CompanionMemoryToolContext memoryContext) {}
