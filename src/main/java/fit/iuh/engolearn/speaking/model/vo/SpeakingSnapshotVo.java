package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingSnapshotVo implements EmbeddedDocument {
    @Field("scope_type") private SpeakingScopeType scopeType;
    @Field("topic_id") private String topicId;
    @Field("topic_item_id") private String topicItemId;
    @Field("title") private String title;
    @Field("topic_title") private String topicTitle;
    @Field("topic_item_title") private String topicItemTitle;
    @Field("content_version") private String contentVersion;
    @Field("profile_version") private long profileVersion;
    @Field("level") private CefrLevel level;
    @Field("description") private String description;
    @Field("ai_role") private String aiRole;
    @Field("learner_role") private String learnerRole;
    @Field("suggested_topics") private List<String> suggestedTopics;
    @Field("resources") private List<SpeakingResourceVo> resources;
    @Field("fallback_reply") private String fallbackReply;
    @Field("fallback_hint") private SpeakingHintVo fallbackHint;
}
