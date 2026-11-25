package fit.iuh.engolearn.companion.service;

import reactor.core.publisher.Flux;

public interface CompanionModel {
    Flux<CompanionModelEvent> stream(CompanionModelRequest request);
}
