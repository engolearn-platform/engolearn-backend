package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProgressResponse {
    public record Metrics(int learnedVocabularyCount, int totalVocabularyCount, int learnedExpressionCount,
                          int totalExpressionCount, int completedQuizCount, int totalQuizCount) {}
    private int percent;
    private Metrics metrics;
    private boolean hasNewContent;
    private String topicId;
    private ItemProgressStatus status;
    private int completedItemCount;
    private int totalItemCount;
    private Instant lastAccessedAt;
    private List<TopicItemProgressResponse> items;
}
