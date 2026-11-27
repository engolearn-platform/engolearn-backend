package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingAccessDto.Reason;
import fit.iuh.engolearn.topic.model.TopicItemVersion;

import java.util.List;

/** Topic-internal release and learner gate shared by Speaking and Topic Learning. */
public interface TopicSpeakingOpenability {
    ReleasedScope releasedScope(SpeakingScopeDto scope);
    Decision openableScope(String learnerId, SpeakingScopeDto scope);

    record Decision(ReleasedScope release, Reason reason) {
        public static Decision allowed(ReleasedScope release) {
            return new Decision(release, null);
        }

        public static Decision denied(Reason reason) {
            return new Decision(null, reason);
        }
    }

    record ReleasedScope(String title, String topicTitle, String topicItemTitle, String version,
                         List<TopicItemVersion> resourceItems) {}
}
