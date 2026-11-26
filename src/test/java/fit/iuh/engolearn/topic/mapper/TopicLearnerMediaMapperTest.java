package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicContextResponse;
import fit.iuh.engolearn.topic.dto.response.TopicDialogueTurnResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicLearnerMediaMapperTest {
    @Test
    void resolvesManagedDialogueAudioAndPreservesLegacyUrls() {
        String reference = "topic-media:topic:asset";
        String signedUrl = "https://media.example.test/authorized";
        String legacyUrl = "https://media.example.test/legacy.mp3";
        TopicMediaService media = mock(TopicMediaService.class);
        when(media.resolveStoredReference("topic", reference)).thenReturn(signedUrl);
        var dialogue = List.of(new TopicDialogueTurnResponse("A", "Hello", "Xin chào", reference),
                new TopicDialogueTurnResponse("B", "Welcome", "Chào mừng", legacyUrl));
        var response = TopicItemContentResponse.builder().context(TopicContextResponse.builder().dialogue(dialogue).build()).build();

        var mapped = new TopicLearnerMediaMapper(media).content("topic", response);

        assertThat(mapped.getContext().getDialogue()).extracting(TopicDialogueTurnResponse::getAudioUrl)
                .containsExactly(signedUrl, legacyUrl);
        assertThat(dialogue.getFirst().getAudioUrl()).isEqualTo(reference);
    }
}
