package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathConfig {

    @Field("duration_weeks")
    private Integer durationWeeks;

    @Field("questions_per_day")
    private Integer questionsPerDay;

    @Field("questions_per_skill_per_week")
    private Integer questionsPerSkillPerWeek;

    public Integer getDurationWeeks() {
        return durationWeeks;
    }

    public Integer getQuestionsPerDay() {
        return questionsPerDay;
    }

    public Integer getQuestionsPerSkillPerWeek() {
        return questionsPerSkillPerWeek;
    }
}
