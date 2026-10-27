package fit.iuh.engolearn.learningpath.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

@Component
public class LearningPathLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(LearningPathLifecycleScheduler.class);

    private final LearningPathLifecycleService lifecycleService;
    private final Clock clock;

    public LearningPathLifecycleScheduler(LearningPathLifecycleService lifecycleService, Clock clock) {
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        Thread.startVirtualThread(this::processDue);
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public void processScheduledDeadlines() {
        processDue();
    }

    private void processDue() {
        try {
            lifecycleService.processDue(clock.instant());
        } catch (RuntimeException exception) {
            log.error("Cannot scan due Learning Paths", exception);
        }
    }
}
