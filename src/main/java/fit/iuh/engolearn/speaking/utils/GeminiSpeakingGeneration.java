package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Prompt;
import fit.iuh.engolearn.speaking.utils.port.ConversationGenerationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeminiSpeakingGeneration implements ConversationGenerationPort {
    private final SpeakingProviderProperties properties;
    private final GeminiStructuredSpeakingGeneration generation;

    @Override
    public String generate(Prompt prompt) {
        var gemini = properties.getGemini();
        return generation.generateStructured(prompt.system(), prompt.data(), prompt.messages(), SpeakingConversation.SCHEMA,
                prompt.opening() ? gemini.getOpeningTimeout() : gemini.getConversationTimeout(), 1200);
    }
}
