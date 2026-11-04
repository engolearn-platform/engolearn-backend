package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import fit.iuh.engolearn.topic.mapper.*;
import fit.iuh.engolearn.topic.model.TopicRetryLocator;
import fit.iuh.engolearn.topic.model.enums.RetryLocatorKind;
import fit.iuh.engolearn.topic.repository.*;
import fit.iuh.engolearn.topic.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TopicRetryOwnershipTest {
    @Test
    void anotherAccountCannotReadOrMutateRetryResources() {
        var persistence = mock(TopicRetryPersistenceRepository.class);
        var generations = mock(TopicGenerationService.class);
        var catalog = mock(TopicLearnerCatalogService.class);
        var service = new TopicRetryServiceImpl(mock(UserLevelLookup.class), catalog,
                mock(TopicItemRepository.class), mock(TopicItemVersionRepository.class), mock(TopicRepository.class),
                mock(TopicVersionRepository.class), persistence, mock(TopicQuizMapper.class), mock(TopicAttemptMapper.class),
                generations, mock(TopicQuizProgressService.class), mock(TopicRetrySessionMapper.class));
        when(persistence.findLocator("attempt-a")).thenReturn(TopicRetryLocator.builder()
                .id("attempt-a").userId("account-a").topicId("topic-a").kind(RetryLocatorKind.ATTEMPT).build());
        when(persistence.findLocator("session-a")).thenReturn(TopicRetryLocator.builder()
                .id("session-a").userId("account-a").topicId("topic-a").kind(RetryLocatorKind.SESSION).build());

        for (Runnable operation : List.<Runnable>of(
                () -> service.owned("account-b", "attempt-a", false),
                () -> service.owned("account-b", "attempt-a", true),
                () -> service.getSession("account-b", "session-a"),
                () -> service.access("account-b", "attempt-a", "key"))) {
            TopicRequestException error = catchThrowableOfType(operation::run, TopicRequestException.class);
            assertThat(error.getStatus()).isEqualTo(404);
            assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        }
        verify(persistence, never()).findRuntime(anyString());
        verifyNoInteractions(generations, catalog);
    }
}
