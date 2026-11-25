package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolPassage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QuestionPoolPassageRepository {
    private final MongoTemplate mongo;

    public QuestionPoolPassage insert(QuestionPoolPassage passage) {
        return mongo.insert(passage);
    }
}
