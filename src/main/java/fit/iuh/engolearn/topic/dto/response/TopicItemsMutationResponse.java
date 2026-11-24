package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record TopicItemsMutationResponse(long revision, List<TopicManagementItemSummary> items) {}
