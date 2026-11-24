package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.model.TopicItemVersion;

import java.util.List;

/** Topic-internal release and learner gate shared by Speaking and Topic Learning. */
public interface TopicSpeakingOpenability {
    ReleasedScope releasedScope(SpeakingScopeDto scope);
    ReleasedScope openableScope(String learnerId, SpeakingScopeDto scope);

    record ReleasedScope(String title, String topicTitle, String topicItemTitle, String version,
                         List<TopicItemVersion> resourceItems) {}
}
