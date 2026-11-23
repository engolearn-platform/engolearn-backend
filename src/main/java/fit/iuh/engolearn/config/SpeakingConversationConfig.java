package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.service.ConversationManager;
import fit.iuh.engolearn.speaking.service.SpeakingConversationComposer;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidator;
import fit.iuh.engolearn.speaking.utils.GeminiSpeakingAiGateway;
import fit.iuh.engolearn.speaking.utils.PromptV2Builder;
import fit.iuh.engolearn.speaking.utils.SpeakingProviderProperties;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(SpeakingProviderProperties.class)
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
            SpeakingProviderProperties properties) {
        var gemini = properties.getGemini();
        return new GeminiSpeakingAiGateway(mapper, promptBuilder, gemini.getEndpoint(), gemini.getModel(),
                gemini.getApiKey(), gemini.getConversationTimeout());
    }

    @Bean
    SpeakingConversationComposer speakingConversationComposer(SpeakingAiGateway gateway,
            SpeakingDraftValidator validator, ConversationManager manager) {
        return new SpeakingConversationComposer(gateway, validator, manager);
    }
}
