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
public class TopicVocabularyResponse {
    private String vocabId;
    private String senseId;
    private String word;
    private String pronunciation;
    private String meaningVi;
    private String partOfSpeech;
    private String usageDescription;
    private String audioUrl;
    private List<TopicWordExampleResponse> examples;
    private TopicQuickCheckResponse quickCheck;
    private Integer orderIndex;
    private String tips;
}
