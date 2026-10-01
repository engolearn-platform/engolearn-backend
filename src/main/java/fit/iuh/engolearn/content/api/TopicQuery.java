package fit.iuh.engolearn.content.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TopicQuery {

    Page<TopicCardView> findPublishedTopics(TopicFilter filter, Pageable pageable);

    Optional<TopicDetailView> getPublishedTopicDetail(String topicId, Integer versionOrNull);

    Map<String, Integer> findVersionNumbers(Collection<String> topicVersionIds);
}
