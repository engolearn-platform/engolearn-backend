package fit.iuh.engolearn.speaking.service;

import java.util.Set;

public interface SpeakingQuota {
    void pending(String sessionId);
    boolean pendingOwner(String ownerId);
    Set<String> recover();
}
