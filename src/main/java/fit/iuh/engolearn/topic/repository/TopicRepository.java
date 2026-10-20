package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.Topic;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicRepository extends MongoRepository<Topic, String>, TopicPageQueries {
    enum SortKey {
        ORDER_INDEX("order_index"),
        DURATION_MINUTES("duration_minutes");

        private final String field;

        SortKey(String field) {
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
