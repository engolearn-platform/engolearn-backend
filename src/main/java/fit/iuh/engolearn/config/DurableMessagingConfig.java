package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@RequiredArgsConstructor
public class DurableMessagingConfig {
    private final MongoMessageTransactions transactions;
    @Bean
    public InitializingBean durableMessageIndexes(List<DurableMessageRepository> repositories) {
        return () -> { if (transactions.available()) repositories.forEach(DurableMessageRepository::ensureStorage); };
    }
    @Bean
    public DurableMessageDispatcher durableMessageDispatcher(List<DurableMessageRepository> repositories,
            List<MessageConsumer> consumers, Clock clock,
            @Value("${engolearn.messaging.lease:PT30S}") Duration lease,
            @Value("${engolearn.messaging.retry-backoff:PT5S}") Duration backoff,
            @Value("${engolearn.messaging.batch-size:20}") int batchSize) {
        return new DurableMessageDispatcher(repositories, consumers, clock, lease, backoff, batchSize);
    }
    @Bean
    public DeliveryTrigger deliveryTrigger(DurableMessageDispatcher dispatcher,
            @Value("${engolearn.messaging.enabled:true}") boolean enabled) {
        return new DeliveryTrigger(dispatcher, transactions, enabled);
    }
    @RequiredArgsConstructor
    public static class DeliveryTrigger {
        private final DurableMessageDispatcher dispatcher;
        private final MongoMessageTransactions transactions;
        private final boolean enabled;
        @Scheduled(fixedDelayString = "${engolearn.messaging.poll-delay-ms:1000}")
        public void dispatch() { if (enabled && transactions.available()) dispatcher.dispatch(); }
    }
}
