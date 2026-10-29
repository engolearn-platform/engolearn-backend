package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import java.util.Date;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathDraft implements EmbeddedDocument {

    @Field("selected_option_id")
    private String selectedOptionId;

    @Field("saved_at")
    private Date savedAt;

    public String getSelectedOptionId() {
        return selectedOptionId;
    }

    public Date getSavedAt() {
        return savedAt;
    }
}
