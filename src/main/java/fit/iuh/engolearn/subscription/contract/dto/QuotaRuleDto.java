package fit.iuh.engolearn.subscription.contract.dto;

public record QuotaRuleDto(String quotaType, String displayName, long limit, String unit, String period, int displayOrder) {}
