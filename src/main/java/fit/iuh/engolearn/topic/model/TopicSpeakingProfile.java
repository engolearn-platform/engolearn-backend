package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.vo.TopicSpeakingProfileVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("topic_speaking_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class TopicSpeakingProfile extends BaseDocument {
    @Version @Field("revision") private Long revision;
    @Field("draft") private TopicSpeakingProfileVo draft;
    @Field("published") private TopicSpeakingProfileVo published;
    @Field("published_content_version") private String publishedContentVersion;
    @Field("published_profile_version") private Long publishedProfileVersion;
}
