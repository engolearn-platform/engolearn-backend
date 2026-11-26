package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
class TopicSpeakingContextBudget {
    // Reserve for six bounded STT messages, five replies, JSON-escaped memory,
    // system instructions, schema, request framing and generated output.
    static final int DYNAMIC_RESERVE_BYTES = 256 * 1024;

    private final ObjectMapper mapper;
    private final int totalLimitBytes;

    TopicSpeakingContextBudget(ObjectMapper mapper,
                               @Value("${speaking.context.total-limit-bytes:786432}") int totalLimitBytes) {
        if (totalLimitBytes <= DYNAMIC_RESERVE_BYTES) {
            throw new IllegalArgumentException("Speaking context budget must exceed the conversation reserve");
        }
        this.mapper = mapper;
        this.totalLimitBytes = totalLimitBytes;
    }

    boolean fits(ReleasedScope release, TopicSpeakingProfileDto profile, List<TopicSpeakingResourceDto> resources) {
        var context = new StaticContext(release.title(), release.topicTitle(), release.topicItemTitle(),
                profile.level(), profile.description(), profile.aiRole(), profile.learnerRole(), profile.suggestedTopics(),
                profile.fallbackReply(), profile.fallbackHint(), resources.stream()
                        .map(resource -> new Resource(resource.id(), resource.type(), resource.textEn(), resource.context()))
                        .toList());
        return mapper.writeValueAsBytes(context).length <= totalLimitBytes - DYNAMIC_RESERVE_BYTES;
    }

    private record StaticContext(String title, String topicTitle, String topicItemTitle, Object level, String description,
                                 String aiRole, String learnerRole, List<String> suggestedTopics, String fallbackReply,
                                 TopicSpeakingProfileDto.Hint fallbackHint, List<Resource> resources) {}

    private record Resource(String id, TopicSpeakingResourceDto.Type type, String textEn, String context) {}
}
