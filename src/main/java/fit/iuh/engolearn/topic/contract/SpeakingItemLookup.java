package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.SpeakingItemDto;

import java.util.Optional;

public interface SpeakingItemLookup {
    Optional<SpeakingItemDto> findOpenableItem(String itemId, String learnerId);
}
