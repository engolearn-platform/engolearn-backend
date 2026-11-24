package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.model.vo.TopicMediaReferenceVo;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.media.contract.MediaManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicMediaServiceImplTest {
    private static final String IMAGE_ID = "4f33a9e6-9331-4713-a9cb-104f8e4c2117";
    private static final String AUDIO_ID = "1cdd8f4a-a66a-4331-96db-8278aa0a4019";

    @Mock private MediaManagement media;
    @Mock private TopicRepository topics;
    @Mock private MongoTemplate mongo;

    private TopicMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopicMediaServiceImpl(media, topics, mongo);
    }

    @Test
    void onlyAcceptsAudioMarkersRegisteredToTheSameTopicAsAudio() {
        Topic topic = topic("topic-1");

        service.validateAudioReference(topic, "https://legacy.example/audio.mp3");
        service.validateAudioReference(topic, null);
        service.validateAudioReference(topic, "topic-media:topic-1:" + AUDIO_ID);

        assertThatThrownBy(() -> service.validateAudioReference(topic, "topic-media:topic-1:" + IMAGE_ID))
                .isInstanceOf(TopicMutationValidationException.class);
        assertThatThrownBy(() -> service.validateAudioReference(topic, "topic-media:topic-2:" + AUDIO_ID))
                .isInstanceOf(TopicMutationValidationException.class);
        assertThat(service.isReference(topic, "topic-media:topic-1:" + IMAGE_ID, TopicMediaKind.IMAGE)).isTrue();
    }

    @Test
    void rejectsExcessiveUploadsAndWrongMediaKindBeforeSendingToStorage() {
        when(topics.findById("topic-1")).thenReturn(Optional.of(topic("topic-1")));
        var oversized = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        var wrongKind = new MockMultipartFile("file", "sound.wav", "audio/wav", new byte[] {1});

        assertThatThrownBy(() -> service.upload("topic-1", TopicMediaKind.IMAGE, oversized))
                .isInstanceOf(TopicMutationValidationException.class);
        assertThatThrownBy(() -> service.upload("topic-1", TopicMediaKind.IMAGE, wrongKind))
                .isInstanceOf(TopicMutationValidationException.class);

        verifyNoInteractions(media, mongo);
    }

    private static Topic topic(String id) {
        return Topic.builder().id(id).mediaReferences(List.of(
                new TopicMediaReferenceVo(IMAGE_ID, TopicMediaKind.IMAGE),
                new TopicMediaReferenceVo(AUDIO_ID, TopicMediaKind.AUDIO))).build();
    }
}
