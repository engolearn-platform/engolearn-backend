package fit.iuh.engolearn.config;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests that need a real MongoDB replica set.
 *
 * <p>Uses Testcontainers to start a 1-node replica set with image
 * {@code mongo:8.0.29-noble} (same as docker-compose.yaml), so multi-document
 * transactions work for verifying the saveItemPosition atomicity rule.
 *
 * <p>Authentication is disabled in the test container (no init-mongo.sh is mounted),
 * so Spring Data connects without username/password.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public abstract class AbstractMongoIntegrationTest {

    @Container
    protected static final MongoDBContainer mongo = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0.29-noble"))
            .withCommand("--replSet", "rs0");

    @BeforeAll
    static void initReplicaSet() throws Exception {
        mongo.execInContainer("mongosh", "--eval", "rs.initiate()");
        // Wait for primary election so transactions work
        mongo.waitingFor(org.testcontainers.containers.wait.strategy.Wait
                .forLogMessage(".*ElectionResult.*", 1)
                .withStartupTimeout(Duration.ofSeconds(60)));
    }

    @DynamicPropertySource
    static void mongodbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "engolearn_db");
    }
}
