package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.mapper.PlacementSnapshotMapper;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.service.PlacementAudioDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementAudioDeliveryImpl implements PlacementAudioDelivery {
    private final MediaManagement media;
    private final Environment environment;

    @Override
    public PlacementTestSet prepare(PlacementTestSet source) {
        var urls = new java.util.HashMap<String, String>();
        boolean development = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        if (source.getItems() != null) for (var item : source.getItems()) {
            var audio = item.getAudio();
            if (audio == null) continue;
            if (audio.getMediaId() != null && !audio.getMediaId().isBlank()) {
                urls.computeIfAbsent(audio.getMediaId(), id -> media.resolve(id).url().toString());
            } else if (!development) {
                throw new PlacementRequestException(HttpStatus.SERVICE_UNAVAILABLE,
                        "PLACEMENT_MEDIA_MIGRATION_REQUIRED", "Placement audio needs storage migration.");
            }
        }
        return PlacementSnapshotMapper.copy(source, audio -> {
            if (urls.containsKey(audio.getMediaId())) audio.setUrl(urls.get(audio.getMediaId()));
            return audio;
        });
    }
}
