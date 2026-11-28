package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;
import java.util.List;

public record TopicDiscoveryPageDto(List<RowDto> items, boolean hasMore, String nextCursor,
                                    String coverage, Instant asOf) {
    public TopicDiscoveryPageDto { items = List.copyOf(items); }
    public record RowDto(String topicId, String titleEn, String titleVi, CefrLevel cefrLevel,
                         String descriptionSnippet, int visibleItemCount, ItemDto firstEligibleItem) {}
    public record ItemDto(String itemId, String title, boolean startAllowed) {}
}
