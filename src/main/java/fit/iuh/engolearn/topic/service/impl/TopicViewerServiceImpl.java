package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.infra.dev.LocalLearnerProvider;
import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TopicViewerServiceImpl implements TopicViewerService {
    private final LocalLearnerProvider learners;

    @Override
    public TopicViewerResponse getViewer() {
        TopicLearnerProfile profile = learners.selectedProfile();
        return profile == null ? new TopicViewerResponse("GUEST", null, null, null) :
                new TopicViewerResponse("LEARNER", profile.getId(), profile.getDisplayName(), profile.getCefrLevel());
    }
}
