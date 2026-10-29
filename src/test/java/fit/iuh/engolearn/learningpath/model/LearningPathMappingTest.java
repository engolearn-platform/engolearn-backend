package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathCancelReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPathMappingTest {

    @Test
    void readsExistingStringStatesAndWritesTheSameMongoValues() throws Exception {
        MongoMappingContext context = new MongoMappingContext();
        context.setInitialEntitySet(Set.of(LearningPath.class));
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.afterPropertiesSet();

        Document original = new Document("_id", new ObjectId())
                .append("status", "CANCELLED")
                .append("cancel", new Document("reason", "INACTIVITY"))
                .append("weeks", List.of(new Document("week_no", 1)
                        .append("status", "CLOSED")
                        .append("close_reason", "ALL_DAYS_DONE")
                        .append("days", List.of(new Document("day_no", 1)
                                .append("status", "CLOSED")
                                .append("close_reason", "MIDNIGHT")))));

        LearningPath path = converter.read(LearningPath.class, original);

        assertThat(path.getStatus()).isEqualTo(LearningPathStatus.CANCELLED);
        assertThat(path.getCancel().getReason()).isEqualTo(LearningPathCancelReason.INACTIVITY);
        assertThat(path.getWeeks().getFirst().getStatus()).isEqualTo(LearningPathWeekStatus.CLOSED);
        assertThat(path.getWeeks().getFirst().getCloseReason())
                .isEqualTo(LearningPathWeekCloseReason.ALL_DAYS_DONE);
        assertThat(path.getWeeks().getFirst().getDays().getFirst().getStatus())
                .isEqualTo(LearningPathDayStatus.CLOSED);
        assertThat(path.getWeeks().getFirst().getDays().getFirst().getCloseReason())
                .isEqualTo(LearningPathDayCloseReason.MIDNIGHT);

        Document written = new Document();
        converter.write(path, written);

        assertThat(written.getString("status")).isEqualTo("CANCELLED");
        assertThat(written.get("cancel", Document.class).getString("reason"))
                .isEqualTo("INACTIVITY");
        Document week = written.getList("weeks", Document.class).getFirst();
        assertThat(week.getString("status")).isEqualTo("CLOSED");
        assertThat(week.getString("close_reason")).isEqualTo("ALL_DAYS_DONE");
        Document day = week.getList("days", Document.class).getFirst();
        assertThat(day.getString("status")).isEqualTo("CLOSED");
        assertThat(day.getString("close_reason")).isEqualTo("MIDNIGHT");
    }
}
