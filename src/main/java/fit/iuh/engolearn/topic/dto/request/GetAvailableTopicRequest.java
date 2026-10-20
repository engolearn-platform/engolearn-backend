package fit.iuh.engolearn.topic.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetAvailableTopicRequest {
    private String level;
    private String keyword;
    private String sort;
    private Integer limit;
    private String cursor;
}
