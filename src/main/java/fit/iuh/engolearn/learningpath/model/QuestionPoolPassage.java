package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPoolPassageAudioVo;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("question_pool_passages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuestionPoolPassage extends BaseDocument {
    @Field("passage_id")
    private String passageId;
    @Field("skill")
    private QuestionPoolSkill skill;
    @Field("level")
    private CefrLevel level;
    @Field("title")
    private String title;
    @Field("text")
    private String text;
    @Field("transcript")
    private String transcript;
    @Field("audio_media_id")
    private String audioMediaId;
    @Field("audio")
    private QuestionPoolPassageAudioVo audio;
    @Field("status")
    private QuestionPoolContentStatus status;
    @Field("revision")
    private int revision;
    @Field("created_by")
    private String createdBy;
    @Field("updated_by")
    private String updatedBy;
}
