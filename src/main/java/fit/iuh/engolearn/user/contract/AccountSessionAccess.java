package fit.iuh.engolearn.user.contract;

import fit.iuh.engolearn.user.contract.dto.AccountSessionProofDto;
import java.util.Optional;

/** Originating cookie session. Missing or unavailable proof always denies new work. */
public interface AccountSessionAccess {
    Optional<AccountSessionProofDto> current(String ownerId);
    boolean active(AccountSessionProofDto proof);
}
