package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.QuizFeedbackResponse;
import fit.iuh.engolearn.topic.dto.response.QuizFillBlankResponse;
import fit.iuh.engolearn.topic.dto.response.QuizResponse;
import fit.iuh.engolearn.topic.dto.response.QuizSituationalChoiceResponse;
import fit.iuh.engolearn.topic.dto.response.TopicDialogueTurnResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Generates delivery URLs for response DTOs after the learner's content access has been checked. */
@Component
@RequiredArgsConstructor
public class TopicLearnerMediaMapper {
    private final TopicMediaService media;

    public TopicItemContentResponse content(String topicId, TopicItemContentResponse response) {
        var context = response.getContext();
        if (context != null && context.getDialogue() != null) {
            context.setDialogue(context.getDialogue().stream().map(turn -> managed(turn.getAudioUrl())
                    ? new TopicDialogueTurnResponse(turn.getSpeaker(), turn.getTextEn(), turn.getTextVi(),
                    audio(topicId, turn.getAudioUrl())) : turn).toList());
        }
        for (var word : safe(response.getVocabularies())) {
            word.setAudioUrl(audio(topicId, word.getAudioUrl()));
            word.setAudioEn(audio(topicId, word.getAudioEn()));
            word.setAudioUs(audio(topicId, word.getAudioUs()));
            for (var sample : safe(word.getExamples())) {
                sample.setAudioUrl(audio(topicId, sample.getAudioUrl()));
            }
        }
        for (var block : safe(response.getExpressions())) {
            for (var purpose : safe(block.getPurposes())) {
                for (var expression : safe(purpose.getExamples())) {
                    expression.setAudioUrl(audio(topicId, expression.getAudioUrl()));
                }
            }
        }
        return response;
    }

    public QuizResponse quiz(String topicId, QuizResponse response) {
        return switch (response) {
            case QuizFillBlankResponse quiz -> !managed(quiz.promptAudioUrl()) ? quiz : new QuizFillBlankResponse(
                    quiz.quizId(), quiz.title(), quiz.index(), quiz.totalInType(), quiz.situationVi(),
                    quiz.imageUrl(), quiz.tips(), audio(topicId, quiz.promptAudioUrl()), quiz.type(),
                    quiz.sentenceBefore(), quiz.sentenceAfter(), quiz.options());
            case QuizSituationalChoiceResponse quiz -> !managed(quiz.promptAudioUrl()) ? quiz : new QuizSituationalChoiceResponse(
                    quiz.quizId(), quiz.title(), quiz.index(), quiz.totalInType(), quiz.situationVi(),
                    quiz.imageUrl(), quiz.tips(), audio(topicId, quiz.promptAudioUrl()), quiz.type(),
                    quiz.questionVi(), quiz.options());
        };
    }

    public QuizFeedbackResponse feedback(String topicId, QuizFeedbackResponse feedback) {
        if (!managed(feedback.audioUrl())) return feedback;
        return new QuizFeedbackResponse(feedback.correctOptionId(), feedback.completedSentenceEn(),
                feedback.translationVi(), feedback.explanationVi(), feedback.grammarNoteVi(), audio(topicId, feedback.audioUrl()));
    }

    private String audio(String topicId, String reference) {
        return managed(reference) ? media.resolveStoredReference(topicId, reference) : reference;
    }

    private static boolean managed(String reference) {
        return reference != null && reference.startsWith("topic-media:");
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
