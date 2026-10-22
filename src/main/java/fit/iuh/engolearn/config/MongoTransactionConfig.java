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
 * <p>MongoDB only supports multi-document transactions on a replica set or on a sharded
 * cluster, while the project's docker setup runs a standalone mongod. The bean is therefore
 * opt-in: enabling it against a standalone server makes every transactional write fail.
 */@Configuration
public class MongoTransactionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "engolearn.mongodb.transactions", name = "enabled", havingValue = "true")
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }
}
