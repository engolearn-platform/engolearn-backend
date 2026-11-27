package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathAudioResponse;
import fit.iuh.engolearn.learningpath.service.LearningPathAudioService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Set;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningPathAudioServiceImpl implements LearningPathAudioService {
    private final MediaManagement media;

    @Override
    public LearningPathAudioResponse resolve(Document passage) {
        if (passage == null) return null;
        Object reference = passage.get("audio_media_id");
        if (reference != null) {
            if (!(reference instanceof String mediaId) || mediaId.isBlank()) {
                throw new IllegalStateException("Learning Path audio media reference is invalid");
            }
            var delivery = media.resolve(mediaId);
            if (delivery == null || delivery.url() == null || delivery.url().getHost() == null
                    || !("http".equals(delivery.url().getScheme()) || "https".equals(delivery.url().getScheme())) || delivery.contentType() == null
                    || !Set.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave").contains(delivery.contentType())
                    || (delivery.expiresAt() != null && !delivery.expiresAt().isAfter(Instant.now()))) {
                throw new MediaStorageUnavailableException("Learning Path audio delivery is unavailable");
            }
            String format = switch (delivery.contentType()) {
                case "audio/mpeg" -> "mp3";
                case "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav";
                default -> null;
            };
            // Never substitute legacy audio or its metadata for a managed recording.
            return new LearningPathAudioResponse(delivery.url().toString(), delivery.expiresAt(),
                    delivery.contentType(), null, format);
        }
        Document legacy = passage.get("audio", Document.class);
        if (legacy == null) return null;
        String url = legacy.getString("url");
        if (url == null || url.isBlank()) throw new IllegalStateException("Learning Path legacy audio URL is missing");
        return new LearningPathAudioResponse(url, null, null, legacy.getInteger("duration_sec"), legacy.getString("format"));
    }
}
