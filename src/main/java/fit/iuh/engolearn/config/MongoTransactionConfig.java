package fit.iuh.engolearn.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Registers the transaction manager that makes the progress writes of one business
 * operation atomic.
 *
 * <p>Both runtime profiles require a replica set and enable this manager. Focused tests
 * can still omit it when they do not exercise transactional persistence.
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "engolearn.mongodb.transactions", name = "enabled", havingValue = "true")
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }
}
