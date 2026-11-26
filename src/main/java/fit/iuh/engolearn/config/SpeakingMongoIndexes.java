package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.utils.SpeakingProviderProperties;
import fit.iuh.engolearn.speaking.utils.SpeakingSessionSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

@Configuration
@EnableConfigurationProperties({SpeakingProviderProperties.class, SpeakingSessionSettings.class})
public class SpeakingMongoIndexes {
    @Bean
    @ConditionalOnProperty(prefix = "engolearn.speaking.indexes", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    InitializingBean speakingIndexes(MongoTemplate mongo) {
        return () -> initialize(mongo);
    }

    public static void initialize(MongoTemplate mongo) {
        var resolver = new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        for (Class<?> type : new Class<?>[]{SpeakingSession.class, SpeakingTurn.class, SpeakingAudioAssessment.class}) {
            resolver.resolveIndexFor(type).forEach(mongo.indexOps(type)::createIndex);
        }
    }
}
