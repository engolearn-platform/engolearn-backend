package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;
import java.util.List;

public record TopicResumePageDto(List<RowDto> items, boolean hasMore, String nextCursor,
                                 String coverage, Instant asOf) {
    public TopicResumePageDto { items = List.copyOf(items); }
    public record RowDto(String topicId, String titleEn, String titleVi, CefrLevel cefrLevel,
                         String learningStatus, int completedVisibleItems, int totalVisibleItems,
                         Instant lastAccessedAt, ActiveItemDto activeLearningItem,
                         TopicDiscoveryPageDto.ItemDto nextEligibleItem) {}
    public record ActiveItemDto(String itemId, String title, String attemptId) {}
}
