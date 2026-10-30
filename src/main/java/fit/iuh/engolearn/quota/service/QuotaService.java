package fit.iuh.engolearn.quota.service;
import fit.iuh.engolearn.quota.dto.response.QuotaUsageSummaryResponse;
public interface QuotaService { QuotaUsageSummaryResponse usage(String userId); }
