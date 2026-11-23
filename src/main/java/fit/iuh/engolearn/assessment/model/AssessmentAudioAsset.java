package fit.iuh.engolearn.assessment.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@Document("assessment_audio_assets")
public class AssessmentAudioAsset extends BaseDocument {
    @Field("media_id") private String mediaId;
    @Field("duration_seconds") private double durationSeconds;
    @Field("lease_token") private String leaseToken;
    @Field("lease_until") private Instant leaseUntil;

    public AssessmentAudioAsset(String id, String mediaId, double durationSeconds) {
        this(id, mediaId, durationSeconds, null, null);
    }

    public AssessmentAudioAsset(String id, String mediaId, double durationSeconds, String leaseToken, Instant leaseUntil) {
        setId(id);
        this.mediaId = mediaId;
        this.durationSeconds = durationSeconds;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
    }
}
