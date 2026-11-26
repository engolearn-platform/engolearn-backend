package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItemVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicItemVersionRepository extends MongoRepository<TopicItemVersion, String> {
    @Query(value = "{ 'topic_id': { $in: ?0 } }", fields = "{ context: 0, vocabularies: 0, expressions: 0, quizzes: 0 }")
    List<TopicItemVersion> findManagementMetadata(Collection<String> topicIds);

    @Query(value = "{ 'topic_id': ?0, 'topic_item_id': ?1 }", fields = "{ context: 0, vocabularies: 0, expressions: 0, quizzes: 0 }")
    List<TopicItemVersion> findManagementMetadataForItem(String topicId, String itemId);

    Optional<TopicItemVersion> findByIdAndTopicIdAndTopicItemId(String id, String topicId, String itemId);

    List<TopicItemVersion> findAllByTopicIdAndTopicItemId(String topicId, String itemId);

    List<TopicItemVersion> findAllByTopicId(String topicId);
}
