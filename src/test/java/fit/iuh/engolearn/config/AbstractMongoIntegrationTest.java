package fit.iuh.engolearn.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Docker gate must run before the Spring context: {@code getReplicaSetUrl(...)} throws
 * before container start when Docker is absent, so subclasses carry
 * {@code @EnabledIfDockerAvailable} ({@code @Testcontainers} on this base is only a second layer).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class AbstractMongoIntegrationTest {

    // Replica-set mode is not automatic in Testcontainers 2.x; withReplicaSet() is the
    // prerequisite for real @Transactional behaviour.
    @Container
    protected static final MongoDBContainer mongo = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0.29-noble"))
            .withReplicaSet();

    private static final String TEST_DB_NAME = "engolearn_it";

    // Suppliers stay lazy so nothing runs when Docker skips; spring.mongodb.database must be
    // registered because application.yaml's value wins over the URI's database path.
    @DynamicPropertySource
    static void mongodbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> mongo.getReplicaSetUrl(TEST_DB_NAME));
        registry.add("spring.mongodb.database", () -> TEST_DB_NAME);
        registry.add("engolearn.mongodb.transactions.enabled", () -> "true");
    }

    @Autowired
    protected MongoTemplate mongoTemplate;

    // Plain spring-web client, not Boot's TestRestTemplate: that lives in
    // spring-boot-resttestclient (needs spring-boot-restclient), which we do not depend on.
    protected RestTemplate restTemplate = new RestTemplate();

    @LocalServerPort
    protected int port;

    // The static container is shared by every subclass; dropping the dedicated IT
    // database before each test prevents fixture leakage across classes.
    @BeforeEach
    void dropTestDatabase() {
        mongoTemplate.getDb().drop();
    }
}
