package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Setter
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
    private String audioEn;
    private String audioUs;
    private List<TopicWordExampleResponse> examples;
    private TopicQuickCheckResponse quickCheck;
    private Integer orderIndex;
    private String tips;
    private String imageUrl;
    private String label;
    private List<TopicCollocationResponse> collocations;
}
