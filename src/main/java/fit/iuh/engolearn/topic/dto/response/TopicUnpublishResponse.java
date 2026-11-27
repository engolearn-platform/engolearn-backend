package fit.iuh.engolearn.topic.dto.response;

public record TopicUnpublishResponse(String topicId, long revision, String status, String latestPublishedVersion) {}