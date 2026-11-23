package fit.iuh.engolearn.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Set;

public class RuntimeProfileInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length != 1 || !Set.of("dev", "prod").contains(profiles[0])) {
            throw new IllegalStateException("Select exactly one runtime profile: dev or prod. "
                    + "Use --spring.profiles.active=dev or --spring.profiles.active=prod.");
        }
        if (!"mongo".equals(environment.getProperty("engolearn.placement.persistence.mode"))
                || !environment.getProperty("engolearn.mongodb.transactions.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Both runtime profiles require Placement Mongo persistence and enabled Mongo transactions.");
        }
        require(environment, "spring.mongodb.replica-set-name");
        if ("prod".equals(profiles[0])) {
            for (String key : new String[]{"spring.mongodb.host", "spring.mongodb.database", "spring.mongodb.username",
                    "spring.mongodb.password", "engolearn.cors.allowed-origins"}) {
                require(environment, key);
            }
            if (!environment.getProperty("engolearn.security.cookies.secure", Boolean.class, false)) {
                throw new IllegalStateException("Production requires secure authentication cookies.");
            }
        }
    }

    private void require(ConfigurableEnvironment environment, String key) {
        try {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return;
            }
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            // Report the property name without exposing environment values.
        }
        throw new IllegalStateException("Missing required runtime configuration: " + key);
    }
}
