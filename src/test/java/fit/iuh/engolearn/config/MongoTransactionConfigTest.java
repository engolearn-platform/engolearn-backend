package fit.iuh.engolearn.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The progress writes are only atomic when a transaction manager backs the
 * {@code @Transactional} boundary, so the manager has to exist once transactions are
 * enabled.
 */
@SpringBootTest(properties = "engolearn.mongodb.transactions.enabled=true")
class MongoTransactionConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theTransactionManagerIsRegisteredWhenTransactionsAreEnabled() {
        assertThat(context.getBeanNamesForType(MongoTransactionManager.class)).isNotEmpty();
    }
}
