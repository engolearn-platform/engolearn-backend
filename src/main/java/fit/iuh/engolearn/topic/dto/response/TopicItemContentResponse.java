package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemContentResponse {
    private String topicItemId;
    private String topicItemVersionId;
    private Integer version;
    private String title;
    private String description;
    private TopicContextResponse context;
    private List<TopicVocabularyResponse> vocabularies;
    private List<TopicExpressionBlockResponse> expressions;
}
