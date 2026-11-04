package fit.iuh.engolearn.user.repository;

import java.time.Instant;

public interface RefreshSessionRepositoryCustom {
    boolean rotateIfCurrent(String sessionId, String accountId, String currentHash, String nextHash, Instant now);
    boolean revokeIfOwnedBy(String sessionId, String accountId);
}
