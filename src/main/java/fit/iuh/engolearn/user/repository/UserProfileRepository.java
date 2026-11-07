package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {}
