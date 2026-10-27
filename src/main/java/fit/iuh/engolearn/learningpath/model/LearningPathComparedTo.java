package fit.iuh.engolearn.learningpath.model;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathComparedTo {

    private String type;

    @Field("week_no")
    private Integer weekNo;

    private Double percent;

    public String getType() {
        return type;
    }

    public Integer getWeekNo() {
        return weekNo;
    }

    public Double getPercent() {
        return percent;
    }
}
