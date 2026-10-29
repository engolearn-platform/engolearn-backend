package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
public class LearningPathDayItem implements EmbeddedDocument {

    private Integer order;

    @Field("question_id")
    private String questionId;

    @Field("micro_skill")
    private String microSkill;

    private LearningPathDraft draft;

    private LearningPathAnswer answer;

    public Integer getOrder() {
        return order;
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getMicroSkill() {
        return microSkill;
    }

    public LearningPathDraft getDraft() {
        return draft;
    }

    public LearningPathAnswer getAnswer() {
        return answer;
    }
}
