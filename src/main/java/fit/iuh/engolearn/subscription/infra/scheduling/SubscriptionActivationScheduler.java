package fit.iuh.engolearn.subscription.infra.scheduling;

import fit.iuh.engolearn.subscription.service.SubscriptionActivationService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class SubscriptionActivationScheduler {
    private final SubscriptionActivationService activation;

    @Scheduled(fixedDelayString="${engolearn.subscription.activation-delay-ms:60000}")
    public void activateDue() {
        activation.activateDue();
    }
}
