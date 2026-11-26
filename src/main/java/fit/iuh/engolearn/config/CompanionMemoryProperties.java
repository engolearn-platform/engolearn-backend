package fit.iuh.engolearn.config;

import java.time.Duration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "engolearn.ai-companion.memory")
public class CompanionMemoryProperties {
    private boolean enabled = true;
    @NotBlank
    private String embeddingModel = "gemini-embedding-2";
    @Min(1)
    @Max(768)
    private int dimensions = 768;
    @NotBlank
    private String qdrantUrl = "http://localhost:6333";
    @NotBlank
    private String collection = "companion_memories";
    @Min(1)
    @Max(10)
    private int topK = 8;
    @Min(1)
    @Max(10)
    private int alwaysOnLimit = 5;
    @Min(1)
    @Max(20000)
    private int maxContextCharacters = 4000;
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double similarityThreshold = 0.35;
    private Duration requestTimeout = Duration.ofSeconds(2);
    @Min(1)
    @Max(500)
    private int syncBatchSize = 100;
    @Min(1)
    @Max(1000)
    private int reconcilePageLimit = 100;
    @Min(1)
    @Max(10000)
    private int reconcileMaxPages = 100;
    private boolean reconcileOnStart;
}
