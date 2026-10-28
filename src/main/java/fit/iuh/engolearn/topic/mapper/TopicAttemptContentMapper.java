package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import java.util.*;

public final class TopicAttemptContentMapper {
    private TopicAttemptContentMapper() {}

    public static TopicItemContentResponse content(TopicItemVersion version) {
        TopicItemContentResponse result = TopicItemMapper.toContentResponse(version.getTopicItemId(), version);
        if (result.getContext() == null || result.getContext().getScenarioDescription() == null) {
            throw new TopicRequestException(404, "CONTENT_UNAVAILABLE", "The saved content has no scenario");
        }
        TopicContextResponse context = result.getContext();
        if (context.getDialogue() == null) context.setDialogue(List.of());
        List<TopicVocabularyResponse> words = result.getVocabularies() == null ? List.of() : result.getVocabularies();
        for (int i = 0; i < words.size(); i++) {
            TopicVocabularyResponse word = words.get(i);
            if (word.getOrderIndex() == null) word.setOrderIndex(i);
            if (word.getExamples() == null) word.setExamples(List.of());
            for (int j = 0; j < word.getExamples().size(); j++) {
                word.getExamples().get(j).setExampleId(version.getId() + ":word:" + i + ":example:" + j);
            }
            var stored = version.getVocabularies().get(i);
            word.setCollocations(stored.getCollocations() == null ? List.of() : stored.getCollocations().stream()
                    .filter(c -> c.getText() != null).map(c -> new TopicCollocationResponse(c.getText(), c.getDescriptionVi())).toList());
            TopicQuickCheckResponse check = word.getQuickCheck();
            if (check != null && (check.getQuestion() == null || check.getOptions() == null ||
                    !check.getOptions().contains(check.getCorrectAnswer()))) word.setQuickCheck(null);
        }
        List<TopicExpressionBlockResponse> groups = result.getExpressions() == null ? List.of() : result.getExpressions();
        for (int g = 0; g < groups.size(); g++) {
            TopicExpressionBlockResponse group = groups.get(g);
            group.setGroupId(version.getId() + ":group:" + g);
            if (group.getPurposes() == null) group.setPurposes(List.of());
            for (int p = 0; p < group.getPurposes().size(); p++) {
                TopicExpressionPurposeResponse purpose = group.getPurposes().get(p);
                purpose.setPurposeId(group.getGroupId() + ":purpose:" + p);
                if (purpose.getOrderIndex() == null) purpose.setOrderIndex(p);
                if (purpose.getExamples() == null) purpose.setExamples(List.of());
                for (int e = 0; e < purpose.getExamples().size(); e++) {
                    purpose.getExamples().get(e).setExpressionId(purpose.getPurposeId() + ":example:" + e);
                }
            }
            group.setPurposes(group.getPurposes().stream().sorted(Comparator.comparing(TopicExpressionPurposeResponse::getOrderIndex)
                    .thenComparing(TopicExpressionPurposeResponse::getPurposeId)).toList());
        }
        return TopicItemContentResponse.builder().topicItemId(result.getTopicItemId()).topicItemVersionId(result.getTopicItemVersionId())
                .version(result.getVersion()).title(result.getTitle()).description(result.getDescription()).context(context)
                .vocabularies(words.stream().sorted(Comparator.comparing(TopicVocabularyResponse::getOrderIndex)
                        .thenComparing(TopicVocabularyResponse::getVocabId)).toList()).expressions(groups).build();
    }
}
