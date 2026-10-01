package fit.iuh.engolearn.learning.progress.dto;

import fit.iuh.engolearn.shared.model.CefrLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetAvailableTopicRequest {
    private String userId;
    private CefrLevel level;
}
