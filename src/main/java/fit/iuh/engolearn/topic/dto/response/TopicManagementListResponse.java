package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

/**
 * E4 list response (design record F05 §4.6): current page + paging echo +
 * unfiltered counters. An empty result is a valid 200 with {@code items: []}.
 */
public record TopicManagementListResponse(
        List<TopicManagementListItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        TopicManagementCounters counters) {
}
