package fit.iuh.engolearn.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The transaction manager is opt-in: the docker dev server is a standalone mongod, which
 * cannot run multi-document transactions, so enabling it there makes every transactional
 * write fail.
 */
class MongoTransactionConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MongoTransactionConfig.class)
            .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class));

    @Test
    void theTransactionManagerIsRegisteredWhenTransactionsAreEnabled() {
        runner.withPropertyValues("engolearn.mongodb.transactions.enabled=true")
                .run(context -> assertThat(context.getBeanNamesForType(MongoTransactionManager.class))
                        .containsExactly("mongoTransactionManager"));
    }

    @Test
    void noTransactionManagerIsRegisteredByDefault() {
        runner.run(context -> assertThat(context.getBeanNamesForType(MongoTransactionManager.class))
                .isEmpty());
    }
}
