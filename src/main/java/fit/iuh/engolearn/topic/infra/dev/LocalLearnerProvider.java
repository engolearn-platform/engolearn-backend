package fit.iuh.engolearn.topic.infra.dev;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import fit.iuh.engolearn.topic.repository.TopicLearnerProfileRepository;
import fit.iuh.engolearn.topic.utils.CurrentUserProvider;
import fit.iuh.engolearn.topic.utils.LearnerLevelProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalLearnerProvider implements CurrentUserProvider, LearnerLevelProvider {
    private final TopicLearnerProfileRepository profiles;
    private final String selectedId;

    public LocalLearnerProvider(TopicLearnerProfileRepository profiles,
            @Value("${engolearn.topic.local-learner-id:}") String selectedId) {
        this.profiles = profiles;
        this.selectedId = selectedId.trim();
    }

    public TopicLearnerProfile selectedProfile() {
        if (selectedId.isEmpty()) return null;
        return profiles.findById(selectedId).orElseThrow(() ->
                new IllegalStateException("Configured local learner profile does not exist"));
    }

    @Override
    public String getCurrentUserId() {
        TopicLearnerProfile profile = selectedProfile();
        return profile == null ? null : profile.getId();
    }

    @Override
    public CefrLevel getCurrentLevel() {
        TopicLearnerProfile profile = selectedProfile();
        return profile == null ? null : profile.getCefrLevel();
    }
}
