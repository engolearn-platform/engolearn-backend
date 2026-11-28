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
        // Both runtime profiles use the same MongoDB connection URI. Replica-set
        // discovery and options are supplied by the URI, not separate host properties.
        require(environment, "spring.mongodb.uri");
        if ("prod".equals(profiles[0])) {
            for (String key : new String[]{"spring.mongodb.database",
                    "engolearn.cors.allowed-origins"}) {
                require(environment, key);
            }
            require(environment, "engolearn.ai-companion.memory.qdrant-url");
            require(environment, "engolearn.ai-companion.memory.qdrant-api-key");
            validateQdrantEndpoint(environment);
            if (!environment.getProperty("engolearn.security.cookies.secure", Boolean.class, false)) {
                throw new IllegalStateException("Production requires secure authentication cookies.");
            }
        }
    }

    private void validateQdrantEndpoint(ConfigurableEnvironment environment) {
        try {
            var uri = java.net.URI.create(environment.getProperty("engolearn.ai-companion.memory.qdrant-url"));
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))) return;
        } catch (IllegalArgumentException ignored) {
            // Never include the supplied endpoint or credentials in the error.
        }
        throw new IllegalStateException("Production Qdrant endpoint must be an HTTPS REST base URL.");
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
