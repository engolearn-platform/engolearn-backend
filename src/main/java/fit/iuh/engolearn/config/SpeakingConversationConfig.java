package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.service.ConversationManager;
import fit.iuh.engolearn.speaking.service.SpeakingConversationComposer;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidator;
import fit.iuh.engolearn.speaking.utils.GeminiSpeakingAiGateway;
import fit.iuh.engolearn.speaking.utils.PromptV2Builder;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
@Profile("!prod")
@ConditionalOnProperty(prefix = "engolearn.speaking",
        name = {"enabled", "manager.enabled"}, havingValue = "true")
public class SpeakingConversationConfig {

    @Bean
    PromptV2Builder speakingPromptV2Builder(ObjectMapper mapper) {
        return new PromptV2Builder(mapper);
    }

    @Bean
    SpeakingDraftValidator speakingDraftValidator(ObjectMapper mapper) {
        return new SpeakingDraftValidator(mapper);
    }

    @Bean
    ConversationManager speakingConversationManager() {
        return new ConversationManager();
    }

    @Bean
    SpeakingAiGateway speakingAiGateway(ObjectMapper mapper, PromptV2Builder promptBuilder,
            @Value("${engolearn.speaking.gemini.endpoint:https://generativelanguage.googleapis.com/v1beta/models}") String endpoint,
            @Value("${engolearn.speaking.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${engolearn.speaking.gemini.api-key:}") String apiKey) {
        return new GeminiSpeakingAiGateway(mapper, promptBuilder, endpoint, model, apiKey,
                Duration.ofSeconds(12));
    }

    @Bean
    SpeakingConversationComposer speakingConversationComposer(SpeakingAiGateway gateway,
            SpeakingDraftValidator validator, ConversationManager manager) {
        return new SpeakingConversationComposer(gateway, validator, manager);
    }
}
