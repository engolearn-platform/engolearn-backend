package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.sound.sampled.AudioSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AssessmentMediaImpl implements AssessmentMedia {
    private final Path directory;
    public AssessmentMediaImpl(@Value("${engolearn.assessment.media-directory:./assessment-media}") String directory) {
        this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    public byte[] read(String relative,String sha256,int maxSeconds) {
        try {
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
    public AssessmentMediaResponse sample() {return encode(read("sound-check.wav",null,50));}
}
