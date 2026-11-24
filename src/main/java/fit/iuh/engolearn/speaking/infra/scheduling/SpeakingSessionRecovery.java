package fit.iuh.engolearn.speaking.infra.scheduling;

import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.service.SpeakingQuota;
import fit.iuh.engolearn.speaking.service.SpeakingRealtime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.speaking.recovery", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SpeakingSessionRecovery {
    private final SpeakingRuntime runtime;
    private final SpeakingRealtime realtime;
    private final SpeakingQuota quota;

    @Scheduled(fixedDelayString = "5s")
    void recover() {
        runtime.recover();
        realtime.recover();
        quota.recover();
    }
}
