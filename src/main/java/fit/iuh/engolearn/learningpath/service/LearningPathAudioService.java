package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathAudioResponse;
import org.bson.Document;

public interface LearningPathAudioService {
    // The owning flow must authorize content access before requesting delivery.
    LearningPathAudioResponse resolve(Document passage);
}
