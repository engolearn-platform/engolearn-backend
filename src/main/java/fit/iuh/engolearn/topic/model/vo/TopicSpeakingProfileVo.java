package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TopicSpeakingProfileVo implements EmbeddedDocument {
    @Field("enabled") private boolean enabled;
    @Field("level") private CefrLevel level;
    @Field("description") private String description;
    @Field("ai_role") private String aiRole;
    @Field("learner_role") private String learnerRole;
    @Field("suggested_topics") private List<String> suggestedTopics;
    @Field("selected_resource_ids") private List<String> selectedResourceIds;
    @Field("fallback_reply") private String fallbackReply;
    @Field("example_en") private String hintExampleEn;
    @Field("explanation_vi") private String hintExplanationVi;
}
