package fit.iuh.engolearn.config;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

@Configuration
public class LearningPathIndexConfig {
    @Bean
    ApplicationRunner learningPathIndexes(MongoTemplate mongo) {
        return args -> {
            var resolver = new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
            resolver.resolveIndexFor(LearningPath.class).forEach(index -> mongo.indexOps(LearningPath.class).ensureIndex(index));
        };
    }
}
