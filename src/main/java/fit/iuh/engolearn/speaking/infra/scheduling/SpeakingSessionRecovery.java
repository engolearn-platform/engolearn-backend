package fit.iuh.engolearn.speaking.infra.scheduling;

import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
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

    @Scheduled(fixedDelayString = "5s")
    void recover() {
        runtime.recover();
    }
}
