package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemProgressResponse {
    private String topicItemId;
    private ItemProgressStatus status;
    private Integer firstTryCorrectCount;
    private Integer totalQuizCount;
    private Instant completedAt;
}
