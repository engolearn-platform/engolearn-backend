package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathAnswer {

    @Field("selected_option_id")
    private String selectedOptionId;

    @Field("is_correct")
    private Boolean correct;

    @Field("answered_at")
    private Date answeredAt;

    public Boolean getCorrect() {
        return correct;
    }
}
