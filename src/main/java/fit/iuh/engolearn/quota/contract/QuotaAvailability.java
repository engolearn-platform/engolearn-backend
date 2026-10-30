package fit.iuh.engolearn.quota.contract;

import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;

/** A point-in-time quota check; it neither reserves usage nor checks caller permissions. */
public interface QuotaAvailability {
    AvailabilityResultDto checkAvailability(String userId, String quotaType);
}
