package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.CreateLocalLearnerRequest;
import fit.iuh.engolearn.topic.dto.response.LocalLearnerResponse;
import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import fit.iuh.engolearn.topic.repository.TopicLearnerProfileRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class LocalLearnerService {
    private final TopicLearnerProfileRepository profiles;

    public LocalLearnerService(TopicLearnerProfileRepository profiles) {
        this.profiles = profiles;
    }

    public LocalLearnerResponse create(CreateLocalLearnerRequest request) {
        TopicLearnerProfile saved = profiles.insert(new TopicLearnerProfile(null, request.displayName().trim(), request.cefrLevel(), Instant.now()));
        return new LocalLearnerResponse(saved.id(), saved.displayName(), saved.cefrLevel());
    }
}
