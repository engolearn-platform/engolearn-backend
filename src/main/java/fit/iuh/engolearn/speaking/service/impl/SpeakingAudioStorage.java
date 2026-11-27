package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.speaking.model.vo.SpeakingMediaVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Clock;

@Component
@RequiredArgsConstructor
@Slf4j
class SpeakingAudioStorage {
    private final MediaManagement media;
    private final Clock clock;

    SpeakingMediaVo store(String purpose, String reference, byte[] wav) {
        try {
            var result = media.upload(new UploadMediaCommandDto(purpose, MediaAccessPolicy.PROTECTED,
                    reference + ".wav", "audio/wav", wav.length, new ByteArrayInputStream(wav)));
            return new SpeakingMediaVo(result.mediaId(), clock.instant());
        } catch (RuntimeException unavailable) {
            log.warn("Speaking audio storage unavailable for {}", reference);
            return null;
        }
    }
}
