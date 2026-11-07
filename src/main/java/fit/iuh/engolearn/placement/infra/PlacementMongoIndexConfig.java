package fit.iuh.engolearn.placement.infra;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.repository.MongoPlacementAttemptRepository;
import java.time.Instant;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

@Configuration
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementMongoIndexConfig {
    @Bean
    public InitializingBean placementMongoIndexes(MongoTemplate mongo) {
        return () -> {
            var resolver = new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
            for (Class<?> type : new Class<?>[]{PlacementQuestion.class, PlacementTestSet.class,
                    PlacementAttempt.class}) {
                resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).createIndex(index));
            }
            new MongoPlacementAttemptRepository(mongo).verifySessionReservationCompatibility(Instant.now());
        };
    }
}
