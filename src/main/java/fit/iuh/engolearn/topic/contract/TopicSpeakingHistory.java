package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto;

import java.util.Collection;
import java.util.Map;

/** Supplies current learning status and published metadata for Speaking's owned history. */
public interface TopicSpeakingHistory {
    Map<String, TopicSpeakingHistoryDto> summaries(String learnerId, Collection<String> topicIds);

    String publishedTitle(String topicId);

    Map<String, String> publishedCovers(Collection<String> topicIds);
}
