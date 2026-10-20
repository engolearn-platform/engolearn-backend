package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemSummaryResponse {
    private String topicItemId;
    private String title;
    private String description;
    private int durationMinutes;
    private int vocabularyCount;
    private int expressionCount;
    private int quizCount;
}
