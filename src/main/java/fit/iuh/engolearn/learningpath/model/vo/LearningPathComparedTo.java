package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathComparisonType;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathComparedTo implements EmbeddedDocument {

    private LearningPathComparisonType type;

    @Field("week_no")
    private Integer weekNo;

    private Double percent;

    public LearningPathComparisonType getType() {
        return type;
    }

    public Integer getWeekNo() {
        return weekNo;
    }

    public Double getPercent() {
        return percent;
    }
}
