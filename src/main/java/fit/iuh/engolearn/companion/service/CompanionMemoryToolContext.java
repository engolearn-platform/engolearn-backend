package fit.iuh.engolearn.companion.service;

import java.time.Instant;

public record CompanionMemoryToolContext(String conversationId, String turnId, String currentInput,
        boolean admissionAutomaticMemoryEnabled, long forgetRevision, Instant deadline,
        Runnable conversationFence, CompanionMemoryOperationCursor cursor,
        CompanionTurnService.TurnObserver observer) { }
