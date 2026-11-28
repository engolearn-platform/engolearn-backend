package fit.iuh.engolearn.companion.service;

public record MemoryOperationResult(String operationId, MemoryMutation.Action action, String memoryId, long version,
                                    boolean effectCurrent, boolean replayed) { }
