package fit.iuh.engolearn.quota.contract;

import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.contract.dto.UsageResultDto;

/** Joins the caller transaction; callers must roll back their action on denial. */
public interface QuotaConsumption {
    UsageResultDto consume(UsageCommandDto command);
}
