package fit.iuh.engolearn.config;
import fit.iuh.engolearn.assessment.model.*;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
@Configuration
public class AssessmentIndexConfig {
    @Bean ApplicationRunner assessmentIndexes(MongoTemplate mongo,ObjectProvider<MongoTransactionManager> transactions) {
        return args -> {
            if(transactions.getIfAvailable()==null) return;
            var resolver=new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
            for(var type:List.of(AssessmentPaper.class,AssessmentAttempt.class,AssessmentProfile.class,AssessmentGate.class,AssessmentGrantRevocationDocument.class)) {
                if(!mongo.collectionExists(type)) mongo.createCollection(type);
                resolver.resolveIndexFor(type).forEach(index->mongo.indexOps(type).ensureIndex(index));
            }
        };
    }
}
