package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicRetrySessionResponse;
import fit.iuh.engolearn.topic.model.TopicRetryRuntime;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicRetrySessionMapper {
    public TopicRetrySessionResponse toResponse(TopicRetryRuntime state) {
        return new TopicRetrySessionResponse(
                state.getSessionId(), state.getTopicId(),
                state.getScope() == null ? null : state.getScope().name(),
                state.getStatus() == null ? null : state.getStatus().name(),
                state.getItemsSnapshot().stream().map(AttemptItemSnapshotVo::getTopicItemId).toList(),
                state.getCurrentAttempt() == null ? null : state.getCurrentAttempt().getId(),
                state.getStartedAt(), state.getCompletedAt(), List.copyOf(state.getCompletedItemIds()),
                state.getItemsSnapshot().stream().map(item -> new TopicRetrySessionResponse.Snapshot(
                        item.getTopicItemId(), item.getTopicItemVersionId(), item.getOrderIndex())).toList(),
                state.nextItemId());
    }
}
