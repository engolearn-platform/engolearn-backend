package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicContextResponse;
import fit.iuh.engolearn.topic.dto.response.TopicDialogueTurnResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionBlockResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionExampleResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionPurposeResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicQuickCheckResponse;
import fit.iuh.engolearn.topic.dto.response.TopicVocabularyResponse;
import fit.iuh.engolearn.topic.dto.response.TopicWordExampleResponse;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.vo.DialogueTurnVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuickCheckVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSampleVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TopicItemMapper {

    private TopicItemMapper() {
    }

    public static TopicItemContentResponse toContentResponse(String topicItemId, TopicItemVersion version) {
        return TopicItemContentResponse.builder()
                .topicItemId(topicItemId)
                .topicItemVersionId(version.getId())
                .version(version.getVersion())
                .title(version.getTitle())
                .description(version.getDescription())
                .context(toContextResponse(version.getContext()))
                .vocabularies(version.getVocabularies() == null ? null
                        : version.getVocabularies().stream().map(TopicItemMapper::toVocabularyResponse).toList())
                .expressions(version.getExpressions() == null ? null
                        : version.getExpressions().stream().map(TopicItemMapper::toExpressionBlockResponse).toList())
                .build();
    }

    public static TopicContextResponse toContextResponse(TopicContextVo context) {
        if (context == null) {
            return null;
        }
        List<TopicDialogueTurnResponse> dialogue = null;
        if (context.getDialogue() != null) {
            dialogue = context.getDialogue().stream().map(TopicItemMapper::toDialogueTurnResponse).toList();
        }
        return TopicContextResponse.builder()
                .scenarioDescription(context.getScenarioDescription())
                .promptingQuestion(context.getPrompt())
                .dialogue(dialogue)
                .durationSec(context.getDurationSec())
                .tips(context.getTips())
                .build();
    }

    public static TopicDialogueTurnResponse toDialogueTurnResponse(DialogueTurnVo turn) {
        return TopicDialogueTurnResponse.builder()
                .speaker(turn.getSpeaker())
                .textEn(turn.getTextEn())
                .textVi(turn.getTextVi())
                .audioUrl(turn.getAudioUrl())
                .build();
    }

    public static TopicVocabularyResponse toVocabularyResponse(ItemWordVo word) {
        WordSnapshotVo snapshot = word.getSnapshot();
        List<TopicWordExampleResponse> examples = null;
        if (word.getSamples() != null) {
            examples = word.getSamples().stream().map(TopicItemMapper::toWordExampleResponse).toList();
        }
        return TopicVocabularyResponse.builder()
                .vocabId(word.getVocabId())
                .senseId(word.getSenseId())
                .word(snapshot == null ? null : snapshot.getWord())
                .pronunciation(snapshot == null ? null : snapshot.getPhonetic())
                .meaningVi(snapshot == null ? null : snapshot.getDescribeVi())
                .partOfSpeech(snapshot == null ? null : snapshot.getPos())
                // The stored word snapshot carries no usage description, so it stays empty
                // until the content model holds one.
                .usageDescription(null)
                .audioUrl(snapshot == null ? null : snapshot.getAudioUrl())
                .examples(examples)
                .quickCheck(toQuickCheckResponse(word.getQuickCheck()))
                .orderIndex(word.getOrderIndex())
                .tips(word.getTips())
                .build();
    }

    public static TopicWordExampleResponse toWordExampleResponse(WordSampleVo sample) {
        return TopicWordExampleResponse.builder()
                .textEn(sample.getTextEn())
                .textVi(sample.getTextVi())
                .audioUrl(sample.getAudioUrl())
                .build();
    }

    /**
     * The quick check is stored as a sentence plus two free-form maps, while the API shape
     * is a question, options and the correct answer. The values are read by key, accepting
     * both the schema spelling and the camel case one.
     */
    public static TopicQuickCheckResponse toQuickCheckResponse(QuickCheckVo quickCheck) {
        if (quickCheck == null) {
            return null;
        }
        String question = firstText(quickCheck.getQuizData(), "question");
        if (question == null && quickCheck.getSentence() != null) {
            question = quickCheck.getSentence().getTextEn();
        }
        String correctAnswer = firstText(quickCheck.getAnswerData(), "correct_answer", "correctAnswer", "answer");
        if (correctAnswer == null) {
            correctAnswer = firstText(quickCheck.getQuizData(), "correct_answer", "correctAnswer", "answer");
        }
        return TopicQuickCheckResponse.builder()
                .question(question)
                .options(textList(quickCheck.getQuizData(), "options"))
                .correctAnswer(correctAnswer)
                .build();
    }

    public static TopicExpressionBlockResponse toExpressionBlockResponse(ExpressionBlockVo block) {
        List<TopicExpressionPurposeResponse> purposes = null;
        if (block.getPurposes() != null) {
            purposes = block.getPurposes().stream().map(TopicItemMapper::toExpressionPurposeResponse).toList();
        }
        return TopicExpressionBlockResponse.builder()
                .purposes(purposes)
                .tips(block.getTips())
                .build();
    }

    public static TopicExpressionPurposeResponse toExpressionPurposeResponse(ExpressionPurposeVo purpose) {
        List<TopicExpressionExampleResponse> examples = null;
        if (purpose.getExamples() != null) {
            examples = purpose.getExamples().stream().map(TopicItemMapper::toExpressionExampleResponse).toList();
        }
        return TopicExpressionPurposeResponse.builder()
                .purposeEn(purpose.getPurposeEn())
                .purposeVi(purpose.getPurposeVi())
                .orderIndex(purpose.getOrderIndex())
                .examples(examples)
                .build();
    }

    public static TopicExpressionExampleResponse toExpressionExampleResponse(ExpressionExampleVo example) {
        return TopicExpressionExampleResponse.builder()
                .type(example.getType())
                .textEn(example.getTextEn())
                .textVi(example.getTextVi())
                .usageNote(example.getUsageNote())
                .audioUrl(example.getAudioUrl())
                .build();
    }

    private static String firstText(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static List<String> textList(Map<String, Object> source, String... keys) {
        if (source == null) {
            return List.of();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Iterable<?> values) {
                List<String> texts = new ArrayList<>();
                for (Object element : values) {
                    if (element != null) {
                        texts.add(String.valueOf(element));
                    }
                }
                return texts;
            }
        }
        return List.of();
    }
}
