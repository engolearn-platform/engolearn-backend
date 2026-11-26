package fit.iuh.engolearn.topic.dto.request;

import java.util.List;

public record TopicItemOrderRequest(Number revision, List<String> itemIds) {}
