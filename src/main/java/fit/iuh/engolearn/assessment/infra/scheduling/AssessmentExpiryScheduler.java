package fit.iuh.engolearn.assessment.infra.scheduling;
import fit.iuh.engolearn.assessment.service.AssessmentService;

import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class AssessmentExpiryScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(AssessmentExpiryScheduler.class);
    private final AssessmentRepository store;private final AssessmentService service;private final Clock clock;
    @Scheduled(fixedDelayString="${engolearn.assessment.expiry-delay-ms:1000}")
    public void expire() {
        for(var a:store.due(clock.instant())) {
            try {service.expire(a.getUserId(),a.getId());}
            catch(RuntimeException e) {LOG.warn("Assessment deadline settlement failed for attempt {}",a.getId(),e);}
        }
    }
}
