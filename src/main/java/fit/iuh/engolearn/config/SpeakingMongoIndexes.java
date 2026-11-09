package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Configuration
@ConditionalOnProperty(prefix = "engolearn.speaking.indexes", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SpeakingMongoIndexes {
    @Bean
    public ApplicationRunner speakingIndexes(MongoTemplate mongo) {
        return args -> {
            mongo.indexOps(SpeakingItemConfig.class).ensureIndex(new Index()
                    .on("topic_item_version_id", Sort.Direction.ASC).unique());
            mongo.indexOps(SpeakingSession.class).ensureIndex(new Index()
                    .on("open_learner_id", Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("open_learner_id").type(2))));
            mongo.indexOps(SpeakingTurn.class).ensureIndex(new Index()
                    .on("session_id", Sort.Direction.ASC).on("sequence", Sort.Direction.ASC).unique());
        };
    }
}
