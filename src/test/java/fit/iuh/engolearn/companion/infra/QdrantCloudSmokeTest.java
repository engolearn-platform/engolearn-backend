package fit.iuh.engolearn.companion.infra;

import static org.assertj.core.api.Assertions.assertThat;

import fit.iuh.engolearn.EngolearnApplication;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import fit.iuh.engolearn.config.RuntimeProfileInitializer;
import fit.iuh.engolearn.companion.service.MemoryEmbeddingService;
import fit.iuh.engolearn.companion.service.MemoryVectorStore;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Explicit opt-in; uses a disposable MongoDB and a unique, temporary cloud collection. */
@EnabledIfSystemProperty(named = "engolearn.qdrant.cloud.smoke", matches = "true")
class QdrantCloudSmokeTest {
    @Test
    void prodBackendUsesCloudForRealEmbeddingAndVectorOperations() {
        String collection = "engo_cloud_smoke_" + UUID.randomUUID().toString().replace("-", "");
        String point = UUID.randomUUID().toString();
        String owner = "synthetic-cloud-smoke";
        Duration timeout = Duration.ofSeconds(15);
        try (var mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble")).withReplicaSet()) {
            mongo.start();
            SpringApplication application = new SpringApplication(EngolearnApplication.class);
            application.addInitializers(new RuntimeProfileInitializer());
            try (var context = application.run("--spring.profiles.active=prod",
                    "--server.address=127.0.0.1", "--server.port=18082",
                    "--spring.mongodb.uri=" + mongo.getReplicaSetUrl("engolearn_cloud_smoke"),
                    "--spring.mongodb.database=engolearn_cloud_smoke",
                    "--engolearn.ai-companion.memory.collection=" + collection,
                    "--engolearn.ai-companion.memory.reconcile-on-start=false",
                    "--spring.devtools.restart.enabled=false")) {
                var properties = context.getBean(CompanionMemoryProperties.class);
                assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
                assertThat(properties.getQdrantUrl()).startsWith("https://");
                assertThat(properties.getQdrantApiKey() != null && !properties.getQdrantApiKey().isBlank()).isTrue();
                assertThat(properties.getCollection()).isEqualTo(collection);
                var health = RestClient.create("http://127.0.0.1:18082").get()
                        .uri("/actuator/health").retrieve().body(String.class);
                assertThat(health).contains("\"UP\"");
                System.out.println("CLOUD_SMOKE: prod HTTP health UP on 18082");
                var store = context.getBean(MemoryVectorStore.class);
                var embedding = context.getBean(MemoryEmbeddingService.class);
                var admin = RestClient.builder().baseUrl(properties.getQdrantUrl())
                        .defaultHeader("api-key", properties.getQdrantApiKey()).build();
                try {
                    var vector = embedding.embedDocument("Synthetic learner prefers short English lessons.", timeout);
                    assertThat(vector).hasSize(properties.getDimensions());
                    System.out.println("CLOUD_SMOKE: real Gemini embedding dimensions=" + vector.size());
                    store.upsert(point, vector, Map.of("learner_id", owner, "version", 1,
                            "retrieval_mode", "RELEVANT"), timeout);
                    System.out.println("CLOUD_SMOKE: cloud collection created and point upserted");
                    assertThat(store.search(vector, owner, 8, timeout))
                            .containsExactly(new MemoryVectorStore.VectorMatch(point, owner, 1));
                    assertThat(store.search(vector, "different-synthetic-owner", 8, timeout)).isEmpty();
                    assertThat(store.scroll(null, 10, timeout).points()).hasSize(1);
                    System.out.println("CLOUD_SMOKE: search, owner isolation, scroll passed");
                    store.delete(point, timeout);
                    assertThat(store.search(vector, owner, 8, timeout)).isEmpty();
                    System.out.println("CLOUD_SMOKE: point delete verified");
                } finally {
                    try {
                        admin.delete().uri("/collections/" + collection).retrieve().toBodilessEntity();
                        System.out.println("CLOUD_SMOKE: temporary cloud collection deleted");
                    } catch (RestClientResponseException missing) {
                        if (missing.getStatusCode().value() != 404) throw missing;
                    }
                }
            }
        }
    }
}
