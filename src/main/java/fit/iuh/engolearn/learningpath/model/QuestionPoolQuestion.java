package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPoolOptionVo;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPublicationReviewVo;
import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("question_pool")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuestionPoolQuestion extends BaseDocument {
    @Field("question_id")
    private String questionId;
    @Field("skill")
    private QuestionPoolSkill skill;
    @Field("micro_skill")
    private String microSkill;
    @Field("level")
    private CefrLevel level;
    @Field("passage_id")
    private String passageId;
    @Field("prompt")
    private String prompt;
    @Field("options")
    private List<QuestionPoolOptionVo> options;
    @Field("correct_option_id")
    private String correctOptionId;
    @Field("explanation")
    private String explanation;
    @Field("status")
    private QuestionPoolContentStatus status;
    @Field("revision")
    private Integer revision;
    @Field("created_by")
    private String createdBy;
    @Field("updated_by")
    private String updatedBy;
    @Field("publication_review")
    private QuestionPublicationReviewVo publicationReview;
}
