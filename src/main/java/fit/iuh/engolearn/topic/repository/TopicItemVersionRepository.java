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

    @Query(value = "{ '_id': { $in: ?0 } }", fields = "{ topic_id: 1, topic_item_id: 1, title: 1, description: 1, status: 1, order: 1 }")
    List<TopicItemVersion> findScreenMetadata(Collection<String> ids);

    @Query(value = "{ '_id': ?0 }", fields = "{ topic_id: 1, topic_item_id: 1, title: 1, description: 1, status: 1, version: 1, 'context.scenario_description': 1, 'context.prompt': 1, 'context.dialogue.speaker': 1, 'context.dialogue.text_en': 1, 'context.dialogue.text_vi': 1, 'context.tips': 1 }")
    Optional<TopicItemVersion> findScreenDialogue(String id);

    @Query(value = "{ '_id': ?0 }", fields = "{ topic_id: 1, topic_item_id: 1, title: 1, description: 1, status: 1, version: 1, 'context.scenario_description': 1, 'vocabularies.vocab_id': 1, 'vocabularies.order_index': 1, 'vocabularies.snapshot.word': 1, 'vocabularies.snapshot.pos': 1, 'vocabularies.snapshot.describe_vi': 1, 'vocabularies.snapshot.phonetic': 1, 'vocabularies.samples.text_en': 1, 'vocabularies.samples.text_vi': 1, 'vocabularies.collocations.text': 1, 'vocabularies.collocations.description_vi': 1, 'vocabularies.tips': 1 }")
    Optional<TopicItemVersion> findScreenVocabulary(String id);

    @Query(value = "{ '_id': ?0 }", fields = "{ topic_id: 1, topic_item_id: 1, title: 1, description: 1, status: 1, version: 1, 'context.scenario_description': 1, 'expressions.tips': 1, 'expressions.purposes.purpose_en': 1, 'expressions.purposes.purpose_vi': 1, 'expressions.purposes.order_index': 1, 'expressions.purposes.examples.text_en': 1, 'expressions.purposes.examples.text_vi': 1, 'expressions.purposes.examples.usage_note': 1 }")
    Optional<TopicItemVersion> findScreenExpressions(String id);

    List<TopicItemVersion> findAllByTopicId(String topicId);
}
