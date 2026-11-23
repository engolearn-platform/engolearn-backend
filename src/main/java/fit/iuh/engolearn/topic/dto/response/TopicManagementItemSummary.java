package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;

import java.time.Instant;

public record TopicManagementItemSummary(
        String id,
        String topicId,
        String title,
        String description,
        Integer order,
        ContentStatus status,
        Integer durationMinutes,
        String versionId,
        Integer version,
        String topicVersionId,
        String previousItemVersionId,
        ChangeType changeType,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {
}
