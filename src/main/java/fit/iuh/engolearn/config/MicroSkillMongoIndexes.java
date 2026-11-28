package fit.iuh.engolearn.config;

import fit.iuh.engolearn.microskill.model.MicroSkill;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

@Configuration
public class MicroSkillMongoIndexes {
    @Bean
    @ConditionalOnProperty(prefix = "engolearn.microskill.indexes", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    InitializingBean microSkillIndexes(MongoTemplate mongo) {
        return () -> new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext())
                .resolveIndexFor(MicroSkill.class).forEach(mongo.indexOps(MicroSkill.class)::createIndex);
    }
}
