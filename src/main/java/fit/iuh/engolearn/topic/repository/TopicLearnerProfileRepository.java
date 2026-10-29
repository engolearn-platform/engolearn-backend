package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicLearnerProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicLearnerProfileRepository extends MongoRepository<TopicLearnerProfile, String> {}
