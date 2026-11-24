package fit.iuh.engolearn.speaking.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@ConfigurationProperties(prefix = "engolearn.speaking")
public class SpeakingProviderProperties {
    private final AssemblyAi assemblyai = new AssemblyAi();
    private final Gemini gemini = new Gemini();
    private final Cartesia cartesia = new Cartesia();
    private final Realtime realtime = new Realtime();

    @Getter
    @Setter
    public static class Realtime {
        private int workers = 4;
        private int queueCapacity = 24;
        private int maxConnections = 128;
        private int maxFramesPerSecond = 160;
        private int maxCommandsPerSecond = 16;
    }

    @Getter
    @Setter
    public static class AssemblyAi {
        private String apiKey;
        private String endpoint;
        private String model;
        private Duration connectTimeout;
        private Duration finalizationTimeout;
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String endpoint;
        private String model;
        private Duration conversationTimeout;
        private Duration openingTimeout;
    }

    @Getter
    @Setter
    public static class Cartesia {
        private String apiKey;
        private String voice;
        private String model;
        private String endpoint;
        private String apiVersion;
        private Duration timeout;
    }
}
