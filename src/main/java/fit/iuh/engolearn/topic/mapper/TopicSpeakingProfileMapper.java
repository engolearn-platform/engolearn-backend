package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto.Hint;
import fit.iuh.engolearn.topic.model.vo.TopicSpeakingProfileVo;

public final class TopicSpeakingProfileMapper {
    private TopicSpeakingProfileMapper() {}

    public static TopicSpeakingProfileDto toDto(TopicSpeakingProfileVo value) {
        if (value == null) return null;
        Hint hint = value.getHintExampleEn() == null && value.getHintExplanationVi() == null ? null
                : new Hint(value.getHintExampleEn(), value.getHintExplanationVi());
        return new TopicSpeakingProfileDto(value.isEnabled(), value.getLevel(), value.getDescription(),
                value.getAiRole(), value.getLearnerRole(), value.getSuggestedTopics(), value.getSelectedResourceIds(),
                value.getFallbackReply(), hint);
    }

    public static TopicSpeakingProfileVo toValue(TopicSpeakingProfileDto dto) {
        return TopicSpeakingProfileVo.builder().enabled(dto.enabled()).level(dto.level())
                .description(dto.description()).aiRole(dto.aiRole()).learnerRole(dto.learnerRole())
                .suggestedTopics(dto.suggestedTopics()).selectedResourceIds(dto.selectedResourceIds())
                .fallbackReply(dto.fallbackReply()).hintExampleEn(dto.fallbackHint() == null ? null : dto.fallbackHint().exampleEn())
                .hintExplanationVi(dto.fallbackHint() == null ? null : dto.fallbackHint().explanationVi()).build();
    }
}
