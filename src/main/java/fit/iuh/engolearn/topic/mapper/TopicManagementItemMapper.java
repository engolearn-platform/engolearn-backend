package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.vo.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicManagementItemMapper {

    public TopicManagementItemSummary summary(TopicItemVersion itemVersion) {
        return new TopicManagementItemSummary(
                itemVersion.getTopicItemId(),
                itemVersion.getTopicId(),
                itemVersion.getTitle(),
                itemVersion.getDescription(),
                itemVersion.getOrder(),
                itemVersion.getStatus(),
                itemVersion.getDurationMinutes(),
                itemVersion.getId(),
                itemVersion.getVersion(),
                itemVersion.getTopicVersionId(),
                itemVersion.getPreviousItemVersionId(),
                itemVersion.getChangeType(),
                itemVersion.getCreatedAt(),
                itemVersion.getUpdatedAt(),
                itemVersion.getPublishedAt());
    }

    public TopicManagementItemDetail detail(TopicItemVersion itemVersion) {
        return new TopicManagementItemDetail(
                itemVersion.getTopicItemId(),
                itemVersion.getTopicId(),
                itemVersion.getTitle(),
                itemVersion.getDescription(),
                itemVersion.getOrder(),
                itemVersion.getStatus(),
                itemVersion.getDurationMinutes(),
                itemVersion.getId(),
                itemVersion.getVersion(),
                itemVersion.getTopicVersionId(),
                itemVersion.getPreviousItemVersionId(),
                itemVersion.getChangeType(),
                itemVersion.getCreatedAt(),
                itemVersion.getUpdatedAt(),
                itemVersion.getPublishedAt(),
                map(itemVersion.getContext()),
                map(itemVersion.getVocabularies(), this::map),
                map(itemVersion.getExpressions(), this::map),
                map(itemVersion.getQuizzes(), this::map));
    }

    private TopicManagementItemDetail.Context map(TopicContextVo context) {
        if (context == null) {
            return null;
        }
        return new TopicManagementItemDetail.Context(
                context.getPrompt(), context.getScenarioDescription(), map(context.getDialogue(), this::map),
                context.getDurationSec(), context.getTips());
    }

    private TopicManagementItemDetail.DialogueTurn map(DialogueTurnVo turn) {
        return new TopicManagementItemDetail.DialogueTurn(
                turn.getSpeaker(), turn.getTextEn(), turn.getTextVi(), turn.getAudioUrl());
    }

    private TopicManagementItemDetail.Vocabulary map(ItemWordVo vocabulary) {
        return new TopicManagementItemDetail.Vocabulary(
                vocabulary.getVocabId(), vocabulary.getSenseId(), map(vocabulary.getSnapshot()),
                map(vocabulary.getSamples(), this::map), map(vocabulary.getCollocations(), this::map),
                map(vocabulary.getQuickCheck()), vocabulary.getOrderIndex(), vocabulary.getTips());
    }

    private TopicManagementItemDetail.WordSnapshot map(WordSnapshotVo snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new TopicManagementItemDetail.WordSnapshot(
                snapshot.getWord(), snapshot.getPos(), snapshot.getSense(), snapshot.getDescribeVi(),
                snapshot.getPhonetic(), snapshot.getAudioUrl(), snapshot.getAudioEnUrl(), snapshot.getAudioUsUrl());
    }

    private TopicManagementItemDetail.WordSample map(WordSampleVo sample) {
        return new TopicManagementItemDetail.WordSample(
                sample.getTextEn(), sample.getTextVi(), sample.getAudioUrl(), sample.getIsManual());
    }

    private TopicManagementItemDetail.Collocation map(CollocationVo collocation) {
        return new TopicManagementItemDetail.Collocation(
                collocation.getText(), map(collocation.getExample()), collocation.getDescriptionVi());
    }

    private TopicManagementItemDetail.CollocationExample map(CollocationExampleVo example) {
        if (example == null) {
            return null;
        }
        return new TopicManagementItemDetail.CollocationExample(example.getEn(), example.getVi());
    }

    private TopicManagementItemDetail.QuickCheck map(QuickCheckVo quickCheck) {
        if (quickCheck == null) {
            return null;
        }
        return new TopicManagementItemDetail.QuickCheck(
                map(quickCheck.getSentence()), quickCheck.getQuizData(), quickCheck.getAnswerData(),
                quickCheck.getExplanation());
    }

    private TopicManagementItemDetail.ExpressionBlock map(ExpressionBlockVo expressionBlock) {
        return new TopicManagementItemDetail.ExpressionBlock(
                map(expressionBlock.getPurposes(), this::map), expressionBlock.getTips());
    }

    private TopicManagementItemDetail.ExpressionPurpose map(ExpressionPurposeVo purpose) {
        return new TopicManagementItemDetail.ExpressionPurpose(
                purpose.getPurposeEn(), purpose.getPurposeVi(), purpose.getOrderIndex(), purpose.getGroupType(),
                map(purpose.getExamples(), this::map));
    }

    private TopicManagementItemDetail.ExpressionExample map(ExpressionExampleVo example) {
        return new TopicManagementItemDetail.ExpressionExample(
                example.getType(), example.getTextEn(), example.getTextVi(), example.getUsageNote(),
                example.getAudioUrl(), example.getLinkedVocabIds(), example.getLabel());
    }

    private TopicManagementItemDetail.QuizModule map(QuizModuleVo module) {
        return new TopicManagementItemDetail.QuizModule(
                module.getQuizId(), module.getQuizType(), module.getTitle(), module.getInstructions(),
                module.getOrderIndex(), map(module.getQuestions(), this::map));
    }

    private TopicManagementItemDetail.QuizQuestion map(QuizQuestionVo question) {
        return new TopicManagementItemDetail.QuizQuestion(
                question.getPrompt(), question.getAudioUrl(), map(question.getOptions(), this::map),
                question.getCorrect(), question.getExplanationOk(), question.getExplanationNg(),
                map(question.getSentence()), question.getQuizData(), question.getAnswerData(),
                question.getExplanation(), question.getTips());
    }

    private TopicManagementItemDetail.QuizSentence map(QuizSentenceVo sentence) {
        if (sentence == null) {
            return null;
        }
        return new TopicManagementItemDetail.QuizSentence(
                sentence.getTextEn(), sentence.getMeaningVi(), sentence.getAudioUrl(),
                sentence.getAudioUrlSlow(), sentence.getSource(), sentence.getExternalId());
    }

    private TopicManagementItemDetail.QuizOption map(QuizOptionVo option) {
        return new TopicManagementItemDetail.QuizOption(option.getLabel(), option.getTextEn(), option.getIsCorrect());
    }

    private <S, T> List<T> map(List<S> values, java.util.function.Function<S, T> mapper) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(mapper).toList();
    }
}
