package fit.iuh.engolearn.dto.request;

import fit.iuh.engolearn.models.shared.enums.CefrLevel;
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
