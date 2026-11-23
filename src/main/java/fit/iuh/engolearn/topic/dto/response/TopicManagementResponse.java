package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;

import java.time.Instant;
import java.util.List;

public record TopicManagementResponse(String id, String titleEn, String titleVi, CefrLevel cefr,
                                      TopicCategory category, String description, String coverUrl,
                                      String status, long durationMinutes, long itemCount,
                                      Instant createdAt, Instant updatedAt, List<String> tags,
                                      Integer orderIndex, String latestPublishedVersion,
                                      Integer latestBetaVersion, PublicationVersion publicationVersion) {
    public record PublicationVersion(String id, Integer version, ContentStatus status,
                                     String previousVersionId, Instant publishedAt) {}
}
