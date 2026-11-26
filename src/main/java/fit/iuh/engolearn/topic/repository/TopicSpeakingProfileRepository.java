package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicSpeakingProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicSpeakingProfileRepository extends MongoRepository<TopicSpeakingProfile, String> {}
