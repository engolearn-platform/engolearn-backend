package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import fit.iuh.engolearn.assessment.service.AssessmentCloudAudio;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.sound.sampled.AudioSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class AssessmentMediaImpl implements AssessmentMedia {
    private final Path directory;
    private final AssessmentCloudAudio cloud;
    public AssessmentMediaImpl(@Value("${engolearn.assessment.media-directory:}") String directory,
                               Environment environment, AssessmentCloudAudio cloud) {
        this.cloud = cloud;
        this.directory=environment.acceptsProfiles(Profiles.of("prod")) || directory.isBlank()
                ? null : Path.of(directory).toAbsolutePath().normalize();
    }
    public byte[] read(String relative,String sha256,int maxSeconds) {
        try {
            if(directory==null) throw new IllegalStateException("Assessment media storage is not integrated");
            Path file=directory.resolve(relative).normalize();
            if(!file.startsWith(directory) || !file.toRealPath().startsWith(directory.toRealPath())
                    || !relative.endsWith(".wav") || Files.size(file)>4_000_000) throw new IllegalArgumentException();
            byte[] bytes=Files.readAllBytes(file);
            if(sha256!=null && !MessageDigest.isEqual(HexFormat.of().parseHex(sha256),MessageDigest.getInstance("SHA-256").digest(bytes)))
                throw new IllegalArgumentException();
            try(var audio=AudioSystem.getAudioInputStream(file.toFile())) {
                double seconds=audio.getFrameLength()/audio.getFormat().getFrameRate();
                if(seconds<=0 || seconds>maxSeconds) throw new IllegalArgumentException();
            }
            return bytes;
        } catch(Exception e) {
            throw new AssessmentException(503,"MEDIA_UNAVAILABLE","Không tải được âm thanh. Chưa tạo bài thì chưa tính lượt.");
        }
    }
    public AssessmentMediaResponse encode(byte[] bytes) {return new AssessmentMediaResponse("audio/wav",Base64.getEncoder().encodeToString(bytes));}
    public AssessmentMediaResponse sample() {
        if (directory != null) return encode(read("sound-check.wav", null, 50));
        return cloud.resolve(cloud.find(AssessmentCloudAudio.SOUND_CHECK), 50);
    }

    @Override
    public String durableReference(AssessmentUnitVo unit) {
        if (!unit.hasAudio()) return null;
        if (unit.getAudioMediaId() == null && directory != null) return null;
        var asset = matchingAsset(unit);
        return asset == null ? null : asset.getMediaId();
    }

    @Override
    public AssessmentMediaResponse delivery(AssessmentUnitVo unit, int maxSeconds) {
        if (unit.getAudioMediaId() == null && directory != null) {
            return encode(read(unit.getAudioPath(), unit.getAudioSha256(), maxSeconds));
        }
        return cloud.resolve(matchingAsset(unit), maxSeconds);
    }

    private AssessmentAudioAsset matchingAsset(AssessmentUnitVo unit) {
        var asset = cloud.find(unit.getAudioSha256());
        if (asset != null && unit.getAudioMediaId() != null && !unit.getAudioMediaId().equals(asset.getMediaId())) {
            throw new AssessmentException(503, "MEDIA_UNAVAILABLE", "Tham chiếu âm thanh không khớp.");
        }
        return asset;
    }
}
