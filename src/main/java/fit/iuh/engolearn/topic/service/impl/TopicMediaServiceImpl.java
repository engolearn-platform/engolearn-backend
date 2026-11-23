package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.model.vo.TopicMediaReferenceVo;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TopicMediaServiceImpl implements TopicMediaService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern MEDIA_ID = Pattern.compile("[0-9a-fA-F-]{36}");
    private final MediaManagement media;
    private final TopicRepository topics;
    private final MongoTemplate mongo;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public String upload(String topicId, TopicMediaKind kind, org.springframework.web.multipart.MultipartFile file) {
        Topic topic = topics.findById(topicId).orElseThrow(() -> new TopicNotFoundException("Topic was not found"));
        if (kind == null || file == null || file.isEmpty() || file.getSize() <= 0 || file.getSize() > MAX_BYTES) {
            throw rejected("file", "Media file must be non-empty and no larger than 5 MB.");
        }
        String contentType = file.getContentType();
        List<String> allowed = kind == TopicMediaKind.IMAGE
                ? List.of("image/jpeg", "image/png", "image/webp")
                : List.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave");
        if (contentType == null || !allowed.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw rejected("file", "File content type is not supported for this media kind.");
        }
        try (var input = file.getInputStream()) {
            var result = media.upload(new UploadMediaCommandDto("TOPIC_" + kind.name(), MediaAccessPolicy.PROTECTED,
                    file.getOriginalFilename(), contentType, file.getSize(), input));
            if (mongo.updateFirst(Query.query(Criteria.where("_id").is(topicId)),
                    new Update().addToSet("mediaReferences", TopicMediaReferenceVo.builder()
                            .mediaId(result.mediaId()).kind(kind).build()), Topic.class).getMatchedCount() != 1) {
                throw new TopicNotFoundException("Topic was not found");
            }
            return "topic-media:" + topic.getId() + ":" + result.mediaId();
        } catch (IOException exception) {
            throw new MediaUploadRejectedException("Unable to read uploaded media", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public MediaDeliveryDto resolve(String topicId, String mediaId) {
        Topic topic = topics.findById(topicId).orElseThrow(() -> new MediaNotFoundException(mediaId));
        if (!registered(topic, mediaId)) throw new MediaNotFoundException(mediaId);
        return media.resolve(mediaId);
    }

    @Override
    public boolean isReference(Topic topic, String reference, TopicMediaKind kind) {
        Parsed parsed = parse(reference);
        return topic != null && parsed != null && Objects.equals(topic.getId(), parsed.topicId())
                && topic.getMediaReferences() != null && topic.getMediaReferences().stream()
                .anyMatch(ref -> ref != null && parsed.mediaId().equals(ref.getMediaId()) && ref.getKind() == kind);
    }

    @Override
    public void validateAudioReference(Topic topic, String value) {
        if (value != null && value.startsWith("topic-media:") && !isReference(topic, value, TopicMediaKind.AUDIO)) {
            throw rejected("audioUrl", "Audio media reference must belong to this Topic and be an audio asset.");
        }
    }

    @Override
    public String resolveCover(Topic topic) {
        String reference = topic == null ? null : topic.getCoverUrl();
        if (reference == null || !reference.startsWith("topic-media:")) return reference;
        if (!isReference(topic, reference, TopicMediaKind.IMAGE)) return null;
        return resolveStoredReference(topic.getId(), reference);
    }

    @Override
    public String resolveStoredReference(String topicId, String value) {
        if (value == null || !value.startsWith("topic-media:")) return value;
        Parsed parsed = parse(value);
        if (parsed == null || !Objects.equals(topicId, parsed.topicId())) return null;
        Topic topic = topics.findById(topicId).orElse(null);
        if (topic == null || !registered(topic, parsed.mediaId())) return null;
        try {
            return media.resolve(parsed.mediaId()).url().toString();
        } catch (RuntimeException deliveryFailure) {
            return null;
        }
    }

    private boolean registered(Topic topic, String mediaId) {
        return topic.getMediaReferences() != null && topic.getMediaReferences().stream()
                .anyMatch(ref -> ref != null && mediaId.equals(ref.getMediaId()));
    }

    private Parsed parse(String reference) {
        if (reference == null || !reference.startsWith("topic-media:")) return null;
        String[] pieces = reference.split(":", -1);
        if (pieces.length != 3 || !MEDIA_ID.matcher(pieces[2]).matches()) return null;
        return pieces[1].isBlank() ? null : new Parsed(pieces[1], pieces[2]);
    }

    private static TopicMutationValidationException rejected(String field, String message) {
        return new TopicMutationValidationException(field, message);
    }

    private record Parsed(String topicId, String mediaId) { }
}
