package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.dto.response.MessageResponse;

public record CompanionTurnResult(String turnId, MessageResponse assistantMessage,
                                  int messageCount, int maxMessages, boolean warning) {}
