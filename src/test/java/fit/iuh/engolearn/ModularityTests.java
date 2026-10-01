package fit.iuh.engolearn;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifyModules() {
        ApplicationModules.of(EngolearnApplication.class).verify();
    }
}
