package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.vo.SpeakingLimitsVo;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("engolearn.speaking.session")
public record SpeakingSessionSettings(@DefaultValue("30") int maxTurns, @DefaultValue("20m") Duration maxDuration,
                                      @DefaultValue("120s") Duration maxTurnAudio,
                                      @DefaultValue("15s") Duration closingGrace) {
    public static final Duration TICKET_LIFETIME = Duration.ofSeconds(30);
    public static final Duration AUDIO_RETENTION = Duration.ofDays(30);

    public SpeakingSessionSettings {
        if (maxTurns < 1 || maxDuration.toMillis() < 1 || maxTurnAudio.toMillis() < 1 || closingGrace.toMillis() < 1) {
            throw new IllegalArgumentException("Speaking limits must be positive");
        }
    }

    public SpeakingLimitsVo limits() {
        return new SpeakingLimitsVo(maxTurns, maxDuration.toMillis(), maxTurnAudio.toMillis(), closingGrace.toMillis());
    }
}
