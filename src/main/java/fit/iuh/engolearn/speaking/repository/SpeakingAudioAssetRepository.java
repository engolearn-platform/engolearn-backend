package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingAudioAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpeakingAudioAssetRepository extends MongoRepository<SpeakingAudioAsset, String> {
    List<SpeakingAudioAsset> findBySessionId(String sessionId);
    List<SpeakingAudioAsset> findByTurnId(String turnId);
}
