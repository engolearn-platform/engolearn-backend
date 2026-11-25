package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Prompt;

public interface ConversationGenerationPort {
    String generate(Prompt prompt);
}
