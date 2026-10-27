package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import org.springframework.stereotype.Service;

@Service
public class TopicViewerService {
    private final LocalLearnerProvider learners;

    public TopicViewerService(LocalLearnerProvider learners) { this.learners = learners; }

    public TopicViewerResponse getViewer() {
        TopicLearnerProfile profile = learners.selectedProfile();
        return profile == null ? new TopicViewerResponse("GUEST", null, null, null) :
                new TopicViewerResponse("LEARNER", profile.id(), profile.displayName(), profile.cefrLevel());
    }
}
