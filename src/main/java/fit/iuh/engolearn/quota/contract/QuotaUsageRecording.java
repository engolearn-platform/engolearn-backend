package fit.iuh.engolearn.quota.contract;

import fit.iuh.engolearn.quota.contract.dto.UsageCommand;
import fit.iuh.engolearn.quota.contract.dto.UsageResult;

/** Records measured actual usage, including an amount above the allowance. */
public interface QuotaUsageRecording {
    UsageResult recordUsage(UsageCommand command);
}
