package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import org.springframework.stereotype.Service;

@Service
public class TopicViewerService {
    public TopicViewerResponse getViewer() {
        // No authentication module exists yet; public browsing must not impersonate the legacy fixed learner.
        return new TopicViewerResponse("GUEST", null, null, null);
    }
}
