package fit.iuh.engolearn.user.contract;

import fit.iuh.engolearn.shared.enums.CefrLevel;

import java.util.Optional;

public interface UserLevelLookup {
    /** Returns the persisted current level for this user; empty means the profile or level is absent. */
    Optional<CefrLevel> findLevel(String userId);
}
