package fit.iuh.engolearn.config;

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoClientSettingsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MongoClientSettingsConfig.class);

    @Test
    void theDefaultServerSelectionTimeoutIsTwoSeconds() {
        runner.run(context -> assertThat(serverSelectionTimeoutMillis(context)).isEqualTo(2000L));
    }

    @Test
    void theConfiguredPropertyOverridesTheServerSelectionTimeout() {
        runner.withPropertyValues("engolearn.mongodb.server-selection-timeout-ms=5000")
                .run(context -> assertThat(serverSelectionTimeoutMillis(context)).isEqualTo(5000L));
    }

    private static long serverSelectionTimeoutMillis(ApplicationContext context) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        context.getBean(MongoClientSettingsBuilderCustomizer.class).customize(builder);
        return builder.build().getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS);
    }
}
