package fit.iuh.engolearn.speaking.service;

public interface SpeakingQuota {
    void pending(String sessionId);
    void recover();
}
