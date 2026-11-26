package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingOwnerSlot;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpeakingOwnerSlotRepository extends MongoRepository<SpeakingOwnerSlot, String> {
    java.util.List<SpeakingOwnerSlot> findByStartIsNotNull();
}
