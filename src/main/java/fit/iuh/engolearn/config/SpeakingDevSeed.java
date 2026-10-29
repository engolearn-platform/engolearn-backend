package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;
import fit.iuh.engolearn.speaking.repository.SpeakingItemConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

@Configuration
@Profile("dev")
public class SpeakingDevSeed {
    public record Seed(String topicItemId, String topicItemVersionId, List<SpeakingGoal> goals,
                       int minimumValidTurns) {}

    @Bean
    public ApplicationRunner speakingConfigSeed(SpeakingItemConfigRepository repository,
            ObjectMapper mapper, @Value("${engolearn.speaking.dev-seed-file:}") String path) {
        return args -> {
            if (path.isBlank()) return;
            Seed[] seeds = mapper.readValue(Files.readString(Path.of(path)), Seed[].class);
            HashSet<String> versions = new HashSet<>();
            for (Seed seed : seeds) {
                if (seed.topicItemId() == null || seed.topicItemId().isBlank()
                        || seed.topicItemVersionId() == null || seed.topicItemVersionId().isBlank()
                        || !versions.add(seed.topicItemVersionId()) || seed.goals() == null
                        || seed.goals().size() < 3 || seed.goals().size() > 5
                        || seed.goals().stream().noneMatch(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED)
                        || seed.minimumValidTurns() < 3 || seed.minimumValidTurns() > 5) {
                    throw new IllegalArgumentException("Invalid Speaking seed configuration");
                }
                repository.findByTopicItemVersionId(seed.topicItemVersionId()).ifPresentOrElse(
                        ignored -> { }, () -> repository.save(SpeakingItemConfig.builder()
                                .topicItemId(seed.topicItemId()).topicItemVersionId(seed.topicItemVersionId())
                                .goals(List.copyOf(seed.goals()))
                                .minimumValidTurns(seed.minimumValidTurns()).revision(1)
                                .status(SpeakingItemConfig.Status.PUBLISHED).build()));
            }
        };
    }
}
