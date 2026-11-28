package fit.iuh.engolearn.topic.contract.dto;

import java.util.List;

public record TopicOverviewContextDto(TopicOverviewDto topic, List<ItemDto> items, int visibleItemCount) {
    public TopicOverviewContextDto { items = List.copyOf(items); }
    public record ItemDto(String title, String description) {}
}
