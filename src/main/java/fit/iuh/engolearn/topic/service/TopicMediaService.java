package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import org.springframework.web.multipart.MultipartFile;

public interface TopicMediaService {
    String upload(String topicId, TopicMediaKind kind, MultipartFile file);

    MediaDeliveryDto resolve(String topicId, String mediaId);

    boolean isReference(Topic topic, String reference, TopicMediaKind kind);

    void validateAudioReference(Topic topic, String value);

    String resolveStoredReference(String topicId, String value);

    String resolveCover(Topic topic);
}
