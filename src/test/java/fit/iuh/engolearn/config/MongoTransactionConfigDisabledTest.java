package fit.iuh.engolearn.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default deployment runs a standalone mongod, which cannot run multi-document
 * transactions. The manager stays away unless it is asked for, so the application never
 * claims an atomicity the server cannot deliver.
 */
@SpringBootTest
class MongoTransactionConfigDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noTransactionManagerIsRegisteredByDefault() {
        assertThat(context.getBeanNamesForType(MongoTransactionManager.class)).isEmpty();
    }
}
