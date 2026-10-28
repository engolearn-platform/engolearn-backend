package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicPageResponse {
    private List<TopicSummaryResponse> items;
    private String nextCursor;
    private boolean hasMore;
    private long total;
}
