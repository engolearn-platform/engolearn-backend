package fit.iuh.engolearn.grammar.mapper;

import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.FeedbackResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.OptionResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.PracticeResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.QuestionResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.ResultResponse;
import fit.iuh.engolearn.grammar.model.GrammarPracticeSession;
import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeOrderVo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

public final class GrammarPracticeMapper {
    private GrammarPracticeMapper() {
    }

    public static PracticeResponse session(GrammarPracticeSession session, GrammarLessonContentVo content,
                                           Instant expiresAt, String nextLessonId) {
        int answered = session.getAnswers().size();
        int total = session.getQuestionOrder().size();
        var feedback = IntStream.range(0, answered).mapToObj(index -> feedback(session, content, index)).toList();
        var question = session.getStatus() == GrammarPracticeStatus.IN_PROGRESS ? question(session, content) : null;
        var saved = session.getResult();
        var result = saved == null ? null : new ResultResponse(saved.getScore(), total, saved.isPassed(),
                saved.isCompleted(), saved.isTopicCompleted(), nextLessonId, saved.isArchived(),
                saved.getNewlyUnlockedLevels());
        return new PracticeResponse(session.getId(), session.getTopicId(), session.getLessonId(),
                session.getStatus(), session.getContentVersion(), answered, total, question, feedback, result, expiresAt);
    }

    public static FeedbackResponse feedback(GrammarPracticeSession session, GrammarLessonContentVo content, int index) {
        var answer = session.getAnswers().get(index);
        var order = session.getQuestionOrder().get(index);
        var source = content.getQuestions().get(order.getQuestionIndex());
        boolean multipleChoice = source.getType() == GrammarQuestionType.MULTIPLE_CHOICE;
        String submitted = multipleChoice ? source.getOptions().get(answer.getOptionIndex()) : answer.getAnswerPreview();
        String correct = multipleChoice ? source.getOptions().get(source.getCorrectIndex()) : source.getCorrectAnswer();
        // Grammar User v4: replay only answered questions from the pinned revision, including after reload.
        return new FeedbackResponse(questionId(session.getId(), order.getQuestionIndex()), index + 1,
                answer.isCorrect(), submitted, correct, source.getExplanation(), source.getType(), source.getPrompt());
    }

    public static String questionId(String sessionId, int questionIndex) {
        return publicId(sessionId + ":question:" + questionIndex);
    }

    public static String optionId(String sessionId, int questionIndex, int optionIndex) {
        return publicId(sessionId + ":option:" + questionIndex + ":" + optionIndex);
    }

    private static String publicId(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static QuestionResponse question(GrammarPracticeSession session, GrammarLessonContentVo content) {
        int index = session.getAnswers().size();
        GrammarPracticeOrderVo order = session.getQuestionOrder().get(index);
        int questionIndex = order.getQuestionIndex();
        var source = content.getQuestions().get(questionIndex);
        var options = order.getOptionOrder().stream()
                .map(option -> new OptionResponse(optionId(session.getId(), questionIndex, option),
                        source.getOptions().get(option))).toList();
        return new QuestionResponse(questionId(session.getId(), questionIndex), index + 1,
                source.getType(), source.getPrompt(), options);
    }
}
