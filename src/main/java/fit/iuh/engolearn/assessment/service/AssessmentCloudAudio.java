package fit.iuh.engolearn.assessment.service;

import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import java.io.InputStream;

public interface AssessmentCloudAudio {
    String SOUND_CHECK = "sound-check";

    AssessmentAudioAsset find(String digest);
    AssessmentMediaResponse resolve(AssessmentAudioAsset asset, int maxSeconds);
    void verifyWav(AssessmentAudioAsset asset, int maxSeconds);
    String upload(InputStream input, String expectedDigest, boolean soundCheck);
}
