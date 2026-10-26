package fit.iuh.engolearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Bounds the driver's server-selection wait so boot and health checks fail fast instead of
 * hanging for the 30 s default when Mongo is down. Raise the property when failover
 * tolerance matters more than a quick failure.
 */
@Configuration
public class MongoClientSettingsConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoServerSelectionTimeout(
            @Value("${engolearn.mongodb.server-selection-timeout-ms:2000}") long timeoutMs) {
        return builder -> builder.applyToClusterSettings(
                cluster -> cluster.serverSelectionTimeout(timeoutMs, TimeUnit.MILLISECONDS));
    }
}
