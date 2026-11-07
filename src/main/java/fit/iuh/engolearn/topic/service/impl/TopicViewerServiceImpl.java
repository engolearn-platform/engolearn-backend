package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.user.contract.UserLevelLookup;
import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.topic.dto.response.TopicViewerResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TopicViewerServiceImpl implements TopicViewerService {
    private final UserLevelLookup userLevels;

    @Override
    public TopicViewerResponse getViewer(String userId) {
        if (userId == null) return new TopicViewerResponse("GUEST", null, null, null);
        var level = TopicUserLevelResolver.require(userLevels, userId);
        return new TopicViewerResponse("LEARNER", userId, null, level);
    }
}
