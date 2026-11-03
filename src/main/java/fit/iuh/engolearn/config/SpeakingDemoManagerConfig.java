package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.service.ConversationManager;
import fit.iuh.engolearn.speaking.service.SpeakingDemoComposer;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidator;
import fit.iuh.engolearn.speaking.utils.GeminiSpeakingAiGateway;
import fit.iuh.engolearn.speaking.utils.PromptV2Builder;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;

@Configuration
public class SpeakingDemoManagerConfig {
    private final boolean enabled;

    public SpeakingDemoManagerConfig(Environment environment,
            @Value("${engolearn.speaking.demo.enabled:false}") boolean demoEnabled,
            @Value("${engolearn.speaking.demo.manager.enabled:false}") boolean managerEnabled) {
        this.enabled = demoEnabled && managerEnabled
                && Arrays.stream(environment.getActiveProfiles()).noneMatch("prod"::equals);
    }

    public boolean enabled() {
        return enabled;
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(name = {"engolearn.speaking.demo.enabled",
            "engolearn.speaking.demo.manager.enabled"}, havingValue = "true")
    PromptV2Builder speakingPromptV2Builder(ObjectMapper mapper) {
        return new PromptV2Builder(mapper);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(name = {"engolearn.speaking.demo.enabled",
            "engolearn.speaking.demo.manager.enabled"}, havingValue = "true")
    SpeakingDraftValidator speakingDraftValidator(ObjectMapper mapper) {
        return new SpeakingDraftValidator(mapper);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(name = {"engolearn.speaking.demo.enabled",
            "engolearn.speaking.demo.manager.enabled"}, havingValue = "true")
    ConversationManager speakingConversationManager() {
        return new ConversationManager();
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(name = {"engolearn.speaking.demo.enabled",
            "engolearn.speaking.demo.manager.enabled"}, havingValue = "true")
    SpeakingAiGateway speakingAiGateway(ObjectMapper mapper, PromptV2Builder promptBuilder,
            @Value("${engolearn.speaking.gemini.endpoint:https://generativelanguage.googleapis.com/v1beta/models}") String endpoint,
            @Value("${engolearn.speaking.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${engolearn.speaking.gemini.api-key:}") String apiKey) {
        return new GeminiSpeakingAiGateway(mapper, promptBuilder, endpoint, model, apiKey,
                Duration.ofSeconds(12));
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(name = {"engolearn.speaking.demo.enabled",
            "engolearn.speaking.demo.manager.enabled"}, havingValue = "true")
    SpeakingDemoComposer speakingDemoComposer(SpeakingAiGateway gateway,
            SpeakingDraftValidator validator, ConversationManager manager) {
        return new SpeakingDemoComposer(gateway, validator, manager);
    }
}
