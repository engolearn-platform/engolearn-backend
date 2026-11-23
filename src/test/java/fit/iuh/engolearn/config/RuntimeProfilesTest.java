package fit.iuh.engolearn.config;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.infra.PlacementMediaWebConfig;
import fit.iuh.engolearn.assessment.service.impl.AssessmentMediaImpl;
import fit.iuh.engolearn.placement.repository.MongoPlacementTestSetRepository;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.subscription.controller.DevPurchaseResetController;
import fit.iuh.engolearn.subscription.service.DevPurchaseResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RuntimeProfilesTest {
    private ApplicationContextRunner configuration(String profile) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile,
                        "spring.config.location=classpath:/application.yaml",
                        "MONGO_HOST=localhost", "MONGO_PORT=27017", "MONGO_REPLICA_SET=engolearn-rs",
                        "MONGODB_URI=mongodb://localhost:27017/runtime_profile_test", "PORT=10000",
                        "APP_DB=runtime_profile_test", "APP_USER=test", "APP_PASSWORD=test-only",
                        "AUTH_JWT_SECRET=test-only-signing-secret-at-least-32-bytes",
                        "CORS_ALLOWED_ORIGINS=https://frontend.example.test",
                        "AUTH_COOKIE_SECURE=false", "PLACEMENT_ALLOW_DRAFT=false",
                        "DEV_SPEAKING_FIXTURE_ENABLED=false",
                        "PAYMENT_DEV_RESET_ENABLED=false", "SPEAKING_DEV_SEED_FILE=")
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void actualYamlKeepsRealPersistenceAndTransactions(String profile) {
        configuration(profile).withInitializer(new RuntimeProfileInitializer()).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).containsExactly(profile);
            assertThat(environment.getProperty("engolearn.placement.persistence.mode")).isEqualTo("mongo");
            assertThat(environment.getProperty("engolearn.mongodb.transactions.enabled", Boolean.class)).isTrue();
            if ("dev".equals(profile)) {
                assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
                assertThat(environment.getProperty("spring.mongodb.replica-set-name")).isEqualTo("engolearn-rs");
            } else {
                assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(10000);
                assertThat(environment.getProperty("spring.mongodb.uri"))
                        .isEqualTo("mongodb://localhost:27017/runtime_profile_test");
                assertThat(environment.getProperty("spring.mongodb.database")).isEqualTo("runtime_profile_test");
                assertThat(environment.getProperty("engolearn.mongodb.server-selection-timeout-ms", Integer.class))
                        .isEqualTo(15000);
            }
            assertThat(environment.getProperty("engolearn.payment.dev-reset.enabled", Boolean.class)).isFalse();
        });
    }

    @Test
    void documentedDevEnvironmentAliasesEnableTheHelpers() {
        configuration("dev")
                .withPropertyValues("PAYMENT_DEV_RESET_ENABLED=true", "PLACEMENT_ALLOW_DRAFT=true",
                        "DEV_SPEAKING_FIXTURE_ENABLED=true")
                .withBean(CurrentUserProvider.class, () -> mock(CurrentUserProvider.class))
                .withBean(DevPurchaseResetService.class, () -> mock(DevPurchaseResetService.class))
                .withUserConfiguration(DevPurchaseResetController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DevPurchaseResetController.class);
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("engolearn.placement.test-set.allow-draft", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("engolearn.development.speaking-fixture-enabled", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("engolearn.security.cookies.secure", Boolean.class)).isFalse();
                });
    }

    @Test
    void prodIgnoresDevelopmentAliasesAndHasNoResetController() {
        configuration("prod")
                .withPropertyValues("PAYMENT_DEV_RESET_ENABLED=true", "PLACEMENT_ALLOW_DRAFT=true",
                        "DEV_SPEAKING_FIXTURE_ENABLED=true",
                        "SPEAKING_DEV_SEED_FILE=must-not-run.json")
                .withUserConfiguration(DevPurchaseResetController.class,
                        SpeakingDevFixture.class, SpeakingDevSeed.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(DevPurchaseResetController.class)
                            .doesNotHaveBean(SpeakingDevFixture.class)
                            .doesNotHaveBean(SpeakingDevSeed.class);
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("engolearn.payment.dev-reset.enabled", Boolean.class)).isFalse();
                    assertThat(environment.getProperty("engolearn.placement.test-set.allow-draft", Boolean.class)).isFalse();
                    assertThat(environment.getProperty("engolearn.development.speaking-fixture-enabled", Boolean.class)).isFalse();
                    assertThat(environment.getProperty("engolearn.speaking.dev-seed-file")).isEmpty();
                    assertThat(environment.getProperty("engolearn.security.cookies.secure", Boolean.class)).isTrue();
                });
    }

    @Test
    void legacyTopicSeedAliasDoesNotConfigureStartupSeeding() {
        configuration("dev").withPropertyValues("DEV_TOPIC_SEED_ENABLED=true")
                .withUserConfiguration(SpeakingDevFixture.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SpeakingDevFixture.class);
                    assertThat(context.getEnvironment().getProperty("engolearn.development.topic-seed-enabled")).isNull();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dev,prod", "placement-local", "subscription-local", "dev,other"})
    void runtimeEntryRejectsUnsupportedProfileSelections(String profiles) {
        configuration(profiles).withInitializer(new RuntimeProfileInitializer()).run(context ->
                assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("Select exactly one runtime profile: dev or prod"));
    }

    @Test
    void runtimeEntryRejectsSamplePersistence() {
        configuration("dev").withPropertyValues("engolearn.placement.persistence.mode=sample")
                .withInitializer(new RuntimeProfileInitializer()).run(context ->
                        assertThat(context).hasFailed().getFailure().hasMessageContaining("Placement Mongo persistence"));
    }

    @Test
    void runtimeEntryRejectsDisabledTransactions() {
        configuration("prod").withPropertyValues("engolearn.mongodb.transactions.enabled=false")
                .withInitializer(new RuntimeProfileInitializer()).run(context ->
                        assertThat(context).hasFailed().getFailure().hasMessageContaining("enabled Mongo transactions"));
    }

    @Test
    void prodRequiresMongoUri() {
        configuration("prod").withPropertyValues("MONGODB_URI=")
                .withInitializer(new RuntimeProfileInitializer()).run(context ->
                        assertThat(context).hasFailed().getFailure()
                                .hasMessageContaining("Missing required runtime configuration: spring.mongodb.uri"));
    }

    @Test
    void prodRequiresDatabaseName() {
        configuration("prod").withPropertyValues("APP_DB=")
                .withInitializer(new RuntimeProfileInitializer()).run(context ->
                        assertThat(context).hasFailed().getFailure()
                                .hasMessageContaining("Missing required runtime configuration: spring.mongodb.database"));
    }

    @Test
    void prodDoesNotRequireLocalMongoConnectionFields() {
        configuration("prod").withPropertyValues("MONGO_HOST=", "MONGO_REPLICA_SET=", "APP_USER=", "APP_PASSWORD=")
                .withInitializer(new RuntimeProfileInitializer()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prodStartsWithoutDirectoryConfigurationAndDoesNotRegisterLocalPlacementMedia() {
        configuration("prod").withInitializer(new RuntimeProfileInitializer())
                .withUserConfiguration(PlacementMediaWebConfig.class, AssessmentMediaImpl.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(PlacementMediaWebConfig.class)
                            .hasSingleBean(AssessmentMediaImpl.class);
                    assertThat(context.getEnvironment().getProperty("engolearn.placement.media-directory")).isNull();
                    assertThat(context.getEnvironment().getProperty("engolearn.assessment.media-directory")).isNull();
                });
    }

    @Test
    void prodRejectsInsecureCookieOverride() {
        configuration("prod").withPropertyValues("engolearn.security.cookies.secure=false")
                .withInitializer(new RuntimeProfileInitializer()).run(context ->
                        assertThat(context).hasFailed().getFailure().hasMessageContaining("secure authentication cookies"));
    }

    @Test
    void draftFallbackIsBlockedEvenWhenDevAndProdAreCombinedWithExplicitOptIn() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(any(Query.class), eq(PlacementTestSet.class))).thenReturn(List.of());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "prod");

        assertThat(new MongoPlacementTestSetRepository(mongo, environment, true).findPublished()).isEmpty();

        verify(mongo).find(any(Query.class), eq(PlacementTestSet.class));
    }
}
