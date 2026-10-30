package fit.iuh.engolearn.quota.contract;

import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.contract.dto.UsageResultDto;

/** Records measured actual usage, including an amount above the allowance. */
public interface QuotaUsageRecording {
    UsageResultDto recordUsage(UsageCommandDto command);
}
