package fit.iuh.engolearn.user.contract.dto;

public record AccountSessionProofDto(String accountId, String sessionId, long accountSessionEpoch) {}
