package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.user.contract.UserLevelLookup;

final class TopicUserLevelResolver {
    private TopicUserLevelResolver() {}

    static CefrLevel require(UserLevelLookup userLevels, String userId) {
        return userLevels.findLevel(userId).orElseThrow(() ->
                new TopicRequestException(409, "USER_PROFILE_REQUIRED", "A user profile is required for topic learner operations"));
    }
}
