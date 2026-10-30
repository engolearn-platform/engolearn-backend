package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import java.util.Date;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathAnswer implements EmbeddedDocument {

    @Field("selected_option_id")
    private String selectedOptionId;

    @Field("is_correct")
    private Boolean correct;

    @Field("answered_at")
    private Date answeredAt;

    public Boolean getCorrect() {
        return correct;
    }

    public String getSelectedOptionId() {
        return selectedOptionId;
    }
}
