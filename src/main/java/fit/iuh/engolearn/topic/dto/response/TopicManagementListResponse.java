package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;

import java.time.Instant;
import java.util.List;

public record TopicManagementListResponse(List<Item> items, int page, int size, long totalElements,
                                          int totalPages, Counters counters) {
    public record Item(String id, String titleEn, String titleVi, CefrLevel cefr, TopicCategory category,
                       String status, long itemCount, Instant updatedAt) {}

    public record Counters(long total, long published, long draft) {}
}
