package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRegistrationRepository {
    private final MongoTemplate mongo;

    public User insertUser(User user) {
        return mongo.insert(user);
    }

    public UserProfile insertProfile(UserProfile profile) {
        return mongo.insert(profile);
    }

    public void ensureCollections() {
        if (!mongo.collectionExists("users")) mongo.createCollection("users");
        if (!mongo.collectionExists("user_profiles")) mongo.createCollection("user_profiles");
    }
}
