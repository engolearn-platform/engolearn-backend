package fit.iuh.engolearn.speaking.model;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakingGoalMongoMappingTest {
    private record GoalHolder(List<SpeakingGoal> goals) {}

    @Test
    void goalIdStoredAsIdFieldRoundTripsThroughMongoMapping() throws Exception {
        MongoMappingContext context = new MongoMappingContext();
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.afterPropertiesSet();

        Document stored = new Document("id", "order-iced-coffee")
                .append("title", "Order an iced coffee")
                .append("description", "Ask the barista for an iced coffee.")
                .append("priority", "REQUIRED");

        Document storedConfig = new Document("goals", List.of(stored));

        GoalHolder config = converter.read(GoalHolder.class, storedConfig);
        SpeakingGoal goal = config.goals().getFirst();
        assertEquals("order-iced-coffee", goal.id());

        Document written = new Document();
        converter.write(config, written);
        Document writtenGoal = (Document) ((List<?>) written.get("goals")).getFirst();
        assertEquals("order-iced-coffee", writtenGoal.getString("id"));
    }
}
