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
public class TopicExpressionPurposeResponse {
    private String purposeEn;
    private String purposeVi;
    private Integer orderIndex;
    private List<TopicExpressionExampleResponse> examples;
}
