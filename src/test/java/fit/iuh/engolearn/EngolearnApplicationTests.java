package fit.iuh.engolearn;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Mandatory startup indexes require Mongo; use the disposable server, never the dev database.
@SpringBootTest(properties = {
        "engolearn.speaking.indexes.enabled=false",
        "engolearn.speaking.recovery.enabled=false"})
class EngolearnApplicationTests {

	@DynamicPropertySource
	static void isolatedMongo(DynamicPropertyRegistry registry) {
		AbstractMongoIntegrationTest.registerIsolatedMongo(registry, "engolearn_context_smoke");
	}

	@Test
	void contextLoads() {
	}

}
