package fit.iuh.engolearn.dictionary.repository;

import fit.iuh.engolearn.dictionary.model.Vocabulary;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VocabularyRepository extends MongoRepository<Vocabulary, String> {
}
