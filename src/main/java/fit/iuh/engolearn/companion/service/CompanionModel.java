package fit.iuh.engolearn.companion.service;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface CompanionModel {
    Flux<String> stream(Prompt prompt);
}
