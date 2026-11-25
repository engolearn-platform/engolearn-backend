package fit.iuh.engolearn.speaking.infra.scheduling;

import fit.iuh.engolearn.speaking.service.SpeakingAssessment;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.speaking.recovery", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SpeakingAssessmentScheduler {
    private final SpeakingAssessment assessment;

    @Scheduled(fixedDelayString = "1s")
    void recover() {
        assessment.recover();
    }
}
