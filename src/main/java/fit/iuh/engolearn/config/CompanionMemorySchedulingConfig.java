package fit.iuh.engolearn.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "engolearn.ai-companion.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CompanionMemorySchedulingConfig { }
