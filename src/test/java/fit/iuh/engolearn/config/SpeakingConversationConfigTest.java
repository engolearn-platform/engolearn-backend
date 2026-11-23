package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.media.CloudinaryMediaConfig;
import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.speaking.service.ConversationManager;
import fit.iuh.engolearn.speaking.service.SpeakingConversationComposer;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidator;
import fit.iuh.engolearn.speaking.service.SpeakingOpeningService;
import fit.iuh.engolearn.speaking.service.SpeakingRealtimeService;
import fit.iuh.engolearn.speaking.service.SpeakingSessionService;
import fit.iuh.engolearn.speaking.utils.AssemblyAiSpeechStream;
import fit.iuh.engolearn.speaking.utils.CartesiaSpeechSynthesis;
import fit.iuh.engolearn.speaking.utils.GeminiSpeakingAiGateway;
import fit.iuh.engolearn.speaking.utils.GeminiSpeakingOpeningGenerator;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

class SpeakingConversationConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void wiresManagedConversationWithoutProviderCredentials(String profile) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile,
                        "spring.config.location=classpath:/application.yaml",
                        "engolearn.speaking.gemini.api-key=",
                        "engolearn.speaking.assemblyai.api-key=",
                        "engolearn.speaking.cartesia.api-key=",
                        "engolearn.media.cloudinary.url=", "engolearn.media.cloudinary.cloud-name=",
                        "engolearn.media.cloudinary.api-key=", "engolearn.media.cloudinary.api-secret=")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SpeakingConversationConfig.class, AssemblyAiSpeechStream.class,
                        CartesiaSpeechSynthesis.class, GeminiSpeakingOpeningGenerator.class, CloudinaryMediaConfig.class,
                        SpeakingRealtimeService.class, SpeakingOpeningService.class)
                .withBean(SpeakingSessionService.class, () -> mock(SpeakingSessionService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(SpeakingConversationComposer.class)
                            .hasSingleBean(SpeakingAiGateway.class)
                            .hasSingleBean(SpeakingDraftValidator.class)
                            .hasSingleBean(ConversationManager.class)
                            .hasSingleBean(SpeakingRealtimeService.class)
                            .hasSingleBean(SpeakingOpeningService.class);
                    assertThat(context.getBean(SpeakingAiGateway.class)).isInstanceOf(GeminiSpeakingAiGateway.class);
                    var storage = context.getBean(MediaStorage.class);
                    assertThatIllegalStateException().isThrownBy(() -> storage.uploadPrivateAudio("sample", "wav", new byte[0]))
                            .withMessageContaining("not configured");
                });
    }
}
