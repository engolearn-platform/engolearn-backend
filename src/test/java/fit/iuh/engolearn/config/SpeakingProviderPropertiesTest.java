package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.utils.SpeakingProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpeakingProviderPropertiesTest {
    private ApplicationContextRunner sharedSettings() throws IOException {
        var resources = new YamlPropertySourceLoader().load("shared defaults", new ClassPathResource("application.yaml"));
        return new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    resources.forEach(sources::addLast);
                });
    }

    @Test
    void bindsDocumentedEnvironmentOverridesToProviderSettings() throws IOException {
        sharedSettings().withPropertyValues(
                "ASSEMBLYAI_API_KEY=assembly-secret",
                "SPEAKING_ASSEMBLYAI_ENDPOINT=wss://stt.example.test/v3/ws",
                "SPEAKING_ASSEMBLYAI_MODEL=stt-model",
                "SPEAKING_ASSEMBLYAI_CONNECT_TIMEOUT=3s",
                "SPEAKING_ASSEMBLYAI_FINALIZATION_TIMEOUT=2s",
                "GEMINI_API_KEY=gemini-secret",
                "SPEAKING_GEMINI_ENDPOINT=https://ai.example.test/models",
                "SPEAKING_GEMINI_MODEL=gemini-model",
                "SPEAKING_GEMINI_CONVERSATION_TIMEOUT=4s",
                "SPEAKING_GEMINI_OPENING_TIMEOUT=5s",
                "CARTESIA_API_KEY=cartesia-secret",
                "CARTESIA_VOICE_ID=voice-1",
                "SPEAKING_CARTESIA_MODEL=tts-model",
                "SPEAKING_CARTESIA_ENDPOINT=https://tts.example.test/bytes",
                "SPEAKING_CARTESIA_API_VERSION=2026-08-14",
                "SPEAKING_CARTESIA_TIMEOUT=6s").run(context -> {
            var properties = context.getBean(SpeakingProviderProperties.class);
            assertEquals("wss://stt.example.test/v3/ws", properties.getAssemblyai().getEndpoint());
            assertEquals("stt-model", properties.getAssemblyai().getModel());
            assertEquals("assembly-secret", properties.getAssemblyai().getApiKey());
            assertEquals(Duration.ofSeconds(3), properties.getAssemblyai().getConnectTimeout());
            assertEquals(Duration.ofSeconds(2), properties.getAssemblyai().getFinalizationTimeout());
            assertEquals("https://ai.example.test/models", properties.getGemini().getEndpoint());
            assertEquals("gemini-secret", properties.getGemini().getApiKey());
            assertEquals("gemini-model", properties.getGemini().getModel());
            assertEquals(Duration.ofSeconds(4), properties.getGemini().getConversationTimeout());
            assertEquals(Duration.ofSeconds(5), properties.getGemini().getOpeningTimeout());
            assertEquals("https://tts.example.test/bytes", properties.getCartesia().getEndpoint());
            assertEquals("cartesia-secret", properties.getCartesia().getApiKey());
            assertEquals("tts-model", properties.getCartesia().getModel());
            assertEquals("voice-1", properties.getCartesia().getVoice());
            assertEquals("2026-08-14", properties.getCartesia().getApiVersion());
            assertEquals(Duration.ofSeconds(6), properties.getCartesia().getTimeout());
            assertFalse(properties.toString().contains("secret"));
        });
    }

    @Test
    void sharedYamlProvidesStableDefaultsAndLeavesCredentialsOptional() throws IOException {
        sharedSettings().run(context -> {
            var properties = context.getBean(SpeakingProviderProperties.class);
            assertEquals("wss://streaming.assemblyai.com/v3/ws", properties.getAssemblyai().getEndpoint());
            assertEquals("universal-3-6-pro", properties.getAssemblyai().getModel());
            assertEquals(Duration.ofSeconds(12), properties.getGemini().getConversationTimeout());
            assertEquals(Duration.ofSeconds(10), properties.getGemini().getOpeningTimeout());
            assertEquals("sonic-3.6", properties.getCartesia().getModel());
            assertEquals("2026-08-14", properties.getCartesia().getApiVersion());
            assertEquals("", properties.getGemini().getApiKey());
            assertEquals("", properties.getAssemblyai().getApiKey());
            assertEquals("", properties.getCartesia().getApiKey());
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SpeakingProviderProperties.class)
    static class PropertiesConfiguration { }
}
