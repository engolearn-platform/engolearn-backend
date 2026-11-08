package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingConversationConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(factory -> {
                // Check provider wiring without constructing the HTTP client.
                for (String name : factory.getBeanDefinitionNames()) {
                    factory.getBeanDefinition(name).setLazyInit(true);
                }
            }))
            .withInitializer(context -> {
                try {
                    var sources = new YamlPropertySourceLoader()
                            .load("checked-in configuration", new ClassPathResource("application.yaml"));
                    sources.forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            })
            .withUserConfiguration(SpeakingConversationConfig.class, SpeakingAvailabilityFilter.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void checkedInDefaultsExposeSpeakingWithTheConversationManager() {
        expectMode(runner, true, true);
    }

    @Test
    void canonicalManagerOffKeepsSpeakingOpenForTheLegacyEvaluator() {
        expectMode(runner.withPropertyValues("engolearn.speaking.manager.enabled=false"), true, false);
    }

    @Test
    void legacyOverridesStillWorkWhenCanonicalPropertiesAreAbsent() {
        expectMode(runner.withPropertyValues("engolearn.speaking.demo.manager.enabled=false"), true, false);
        expectMode(runner.withPropertyValues("engolearn.speaking.demo.enabled=false"), false, false);
    }

    @Test
    void canonicalAvailabilityOffOverridesTheLegacyFlag() {
        expectMode(runner.withPropertyValues("engolearn.speaking.enabled=false",
                "engolearn.speaking.demo.enabled=true"), false, false);
    }

    @Test
    void canonicalManagerOnOverridesTheLegacyFlag() {
        expectMode(runner.withPropertyValues("engolearn.speaking.manager.enabled=true",
                "engolearn.speaking.demo.manager.enabled=false"), true, true);
    }

    @Test
    void productionStillBlocksSpeakingEvenWhenExplicitlyEnabled() {
        expectMode(runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", "prod"))
                .withPropertyValues("engolearn.speaking.enabled=true",
                        "engolearn.speaking.manager.enabled=true"), false, false);
    }

    private void expectMode(ApplicationContextRunner settings, boolean available, boolean manager) {
        settings.run(context -> {
            var filter = context.getBean(SpeakingAvailabilityFilter.class);
            for (String path : new String[] {"/api/v1/speaking/sessions", "/ws/speaking/session"}) {
                var request = new MockHttpServletRequest("GET", path);
                var response = new MockHttpServletResponse();
                var chain = new MockFilterChain();
                filter.doFilter(request, response, chain);
                assertEquals(available ? 200 : 404, response.getStatus());
                assertEquals(available ? request : null, chain.getRequest());
            }
            assertEquals(manager ? 1 : 0, context.getBeanNamesForType(SpeakingAiGateway.class).length);
        });
    }
}
