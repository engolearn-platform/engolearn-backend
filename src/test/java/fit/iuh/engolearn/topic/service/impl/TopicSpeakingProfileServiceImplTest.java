package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.exception.TopicSpeakingProfileException;
import fit.iuh.engolearn.topic.mapper.TopicSpeakingProfileMapper;
import fit.iuh.engolearn.topic.model.TopicSpeakingProfile;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicSpeakingProfileRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TopicSpeakingProfileServiceImplTest {
    private static final SpeakingScopeDto SCOPE = new SpeakingScopeDto(SpeakingScopeType.TOPIC, "topic", null);
    private final TopicSpeakingOpenability scopes = mock(TopicSpeakingOpenability.class);
    private final TopicManagementReadService management = mock(TopicManagementReadService.class);
    private final TopicSpeakingProfileRepository profiles = mock(TopicSpeakingProfileRepository.class);
    private final TopicSpeakingProfile state = TopicSpeakingProfile.builder().id("dG9waWM").revision(0L).build();

    @Test
    void changedAuthoritativeReleaseAndStaleDraftRevisionDoNotWritePublication() {
        var service = service(786432);
        state.setDraft(TopicSpeakingProfileMapper.toValue(profile("A conversation at the cafe")));
        when(scopes.releasedScope(SCOPE)).thenReturn(release("v1"), release("v2"));
        assertThatThrownBy(() -> service.publish(SCOPE, 1)).isInstanceOfSatisfying(
                TopicSpeakingProfileException.class, error -> assertThat(error.getCode()).isEqualTo("CONTENT_CHANGED"));
        assertThatThrownBy(() -> service.publish(SCOPE, 0)).isInstanceOfSatisfying(
                TopicSpeakingProfileException.class, error -> assertThat(error.getCode()).isEqualTo("REQUEST_CONFLICT"));
        verify(profiles, never()).save(any());
        assertThat(state.getPublished()).isNull();
    }

    @Test
    void overBudgetDraftCannotReplaceLiveProfile() {
        var service = service(TopicSpeakingContextBudget.DYNAMIC_RESERVE_BYTES + 1024);
        var live = TopicSpeakingProfileMapper.toValue(profile("Cafe"));
        state.setPublished(live);
        state.setDraft(TopicSpeakingProfileMapper.toValue(profile("Conversation ".repeat(300))));
        when(scopes.releasedScope(SCOPE)).thenReturn(release("v1"));

        assertThatThrownBy(() -> service.publish(SCOPE, 1)).isInstanceOfSatisfying(TopicSpeakingProfileException.class,
                error -> assertThat(error.getCode()).isEqualTo("CONTEXT_BUDGET_EXCEEDED"));
        assertThat(state.getPublished()).isSameAs(live);
        verify(profiles, never()).save(any());
    }

    private TopicSpeakingProfileServiceImpl service(int totalLimitBytes) {
        var topic = mock(TopicManagementResponse.class);
        when(topic.titleEn()).thenReturn("Cafe");
        when(management.topic("topic")).thenReturn(topic);
        when(profiles.findById(anyString())).thenReturn(Optional.of(state));
        return new TopicSpeakingProfileServiceImpl(scopes, management, profiles, mock(TopicVersionRepository.class),
                mock(TopicItemVersionRepository.class), mock(CurrentUserProvider.class), Clock.systemUTC(),
                new TopicSpeakingContextBudget(new ObjectMapper(), totalLimitBytes));
    }

    private static ReleasedScope release(String version) {
        return new ReleasedScope("Cafe", "Cafe", null, version, List.of());
    }

    private static TopicSpeakingProfileDto profile(String description) {
        return new TopicSpeakingProfileDto(true, CefrLevel.A1, description, "Barista", "Customer",
                List.of(), List.of(), "Good morning!", null);
    }
}
