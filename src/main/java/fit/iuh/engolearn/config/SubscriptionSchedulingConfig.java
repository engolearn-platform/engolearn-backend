package fit.iuh.engolearn.config;

import fit.iuh.engolearn.subscription.infra.scheduling.SubscriptionActivationScheduler;
import fit.iuh.engolearn.subscription.service.SubscriptionActivationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SubscriptionSchedulingConfig {
    @Bean
    public SubscriptionActivationScheduler subscriptionActivationScheduler(SubscriptionActivationService activation) {
        return new SubscriptionActivationScheduler(activation);
    }
}
