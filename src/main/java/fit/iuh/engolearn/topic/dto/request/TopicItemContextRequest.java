package fit.iuh.engolearn.topic.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import java.math.BigDecimal;

public record TopicItemContextRequest(BigDecimal revision,
                                      @JsonProperty(value = "context", required = true)
                                      TopicManagementItemDetail.Context context) {}
