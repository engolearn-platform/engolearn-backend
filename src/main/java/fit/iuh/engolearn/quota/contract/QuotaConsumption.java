package fit.iuh.engolearn.quota.contract;

import fit.iuh.engolearn.quota.contract.dto.UsageCommand;
import fit.iuh.engolearn.quota.contract.dto.UsageResult;

/** Joins the caller transaction; callers must roll back their action on denial. */
public interface QuotaConsumption {
    UsageResult consume(UsageCommand command);
}
