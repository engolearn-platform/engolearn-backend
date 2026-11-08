package fit.iuh.engolearn.user.contract;

import fit.iuh.engolearn.shared.enums.CefrLevel;

public interface UserLevelPromotion {
    /** Requires the caller's Mongo transaction and an existing profile; returns persisted max(current, requested).
     * Repeats never lower a level or reset progress; unavailable profiles fail rather than being created. */
    CefrLevel promote(String userId, CefrLevel requested);
}
