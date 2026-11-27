package fit.iuh.engolearn.companion.service;

public record CompanionToolExecutionContext(String learnerId, Runnable onAssessmentLookup,
        CompanionMemoryToolContext memoryContext, CompanionBusinessTurnContext businessContext) {
    public CompanionToolExecutionContext(String learnerId, Runnable onAssessmentLookup,
            CompanionMemoryToolContext memoryContext) {
        this(learnerId, onAssessmentLookup, memoryContext, null);
    }
}
