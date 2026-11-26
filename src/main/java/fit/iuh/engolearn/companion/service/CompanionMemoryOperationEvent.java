package fit.iuh.engolearn.companion.service;

public record CompanionMemoryOperationEvent(String operationId, String action,
        String memoryId, long version) {}
