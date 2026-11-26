package fit.iuh.engolearn.config;

import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "engolearn.ai-companion")
public class CompanionProperties {
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.7;
    @Min(1)
    @Max(8192)
    private int maxOutputTokens = 2048;
    private Duration inferenceTimeout = Duration.ofSeconds(30);
    private Duration turnTimeout = Duration.ofSeconds(90);
    @Min(2)
    private int maxMessages = 100;
    @Min(1)
    @Max(128)
    private int maxConcurrentStreams = 16;
    @Min(1)
    @Max(100)
    private int warningPercent = 90;
    @Min(0)
    private int historyMessageLimit = 20;
    @Min(0)
    private int historyTokenBudget = 4000;
    @Min(256)
    @Max(65536)
    private int screenContextMaxBytes = 12000;
    @Min(1)
    @Max(5)
    private int maxToolCalls = 5;
    @Min(1)
    @Max(5)
    private int maxToolRounds = 5;
    private Duration toolTimeout = Duration.ofSeconds(5);
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String personaVersion = "v1";

    @AssertTrue(message = "companion timeouts must be positive")
    public boolean isTimeoutConfigurationValid() {
        return inferenceTimeout != null && !inferenceTimeout.isZero() && !inferenceTimeout.isNegative()
                && inferenceTimeout.toMillis() <= Integer.MAX_VALUE
                && turnTimeout != null && !turnTimeout.isZero() && !turnTimeout.isNegative()
                && toolTimeout != null && !toolTimeout.isZero() && !toolTimeout.isNegative()
                && toolTimeout.toMillis() <= Integer.MAX_VALUE;
    }
}
