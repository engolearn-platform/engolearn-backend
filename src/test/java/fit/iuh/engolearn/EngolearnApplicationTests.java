package fit.iuh.engolearn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Port 1 is closed: keeps this context test off the dev database and makes the guard skip deterministically.
@SpringBootTest(properties = {"spring.mongodb.port=1",
        "engolearn.speaking.indexes.enabled=false",
        "engolearn.speaking.recovery.enabled=false"})
class EngolearnApplicationTests {

	@Test
	void contextLoads() {
	}

}
