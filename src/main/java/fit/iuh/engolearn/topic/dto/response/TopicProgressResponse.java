package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProgressResponse {
    private String topicId;
    private ItemProgressStatus status;
    private int completedItemCount;
    private int totalItemCount;
    private Instant lastAccessedAt;
    private List<TopicItemProgressResponse> items;
}
