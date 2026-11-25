package fit.iuh.engolearn.grammar.service.impl;

import fit.iuh.engolearn.grammar.dto.request.GrammarSubmitAnswerRequest;
import fit.iuh.engolearn.grammar.exception.GrammarRequestException;
import fit.iuh.engolearn.grammar.mapper.GrammarPracticeMapper;
import fit.iuh.engolearn.grammar.model.GrammarPracticeSession;
import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeAnswerVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeOrderVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarQuestionVo;
import fit.iuh.engolearn.grammar.utils.GrammarAnswerNormalizer;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

final class GrammarPracticeQuiz {
    private static final int ANSWER_PREVIEW_LIMIT = 1024;

    private GrammarPracticeQuiz() {
    }

    static List<GrammarPracticeOrderVo> questionOrder(GrammarLessonContentVo content, boolean shuffle) {
        var questions = content.getQuestions();
        var indices = new ArrayList<>(IntStream.range(0, questions.size()).boxed().toList());
        if (shuffle) {
            Collections.shuffle(indices);
        }
        return indices.stream().map(index -> GrammarPracticeOrderVo.builder()
                .questionIndex(index).optionOrder(optionOrder(questions.get(index), shuffle)).build()).toList();
    }

    static GrammarPracticeAnswerVo grade(GrammarPracticeSession session, GrammarPracticeOrderVo order,
                                         GrammarLessonContentVo content, GrammarSubmitAnswerRequest request) {
        int questionIndex = order.getQuestionIndex();
        var question = content.getQuestions().get(questionIndex);
        var answer = GrammarPracticeAnswerVo.builder().requestId(request.requestId());
        if (question.getType() == GrammarQuestionType.MULTIPLE_CHOICE) {
            int selected = order.getOptionOrder().stream()
                    .filter(index -> GrammarPracticeMapper.optionId(session.getId(), questionIndex, index)
                            .equals(request.answer()))
                    .findFirst().orElseThrow(() -> new GrammarRequestException(HttpStatus.BAD_REQUEST,
                            "GRAMMAR_INVALID_ANSWER", "Phương án không thuộc câu hỏi này."));
            return answer.optionIndex(selected).correct(selected == question.getCorrectIndex()).build();
        }

        String entered = GrammarAnswerNormalizer.normalize(request.answer());
        List<String> accepted = question.getAcceptedAnswers();
        boolean matched = entered.equals(GrammarAnswerNormalizer.normalize(question.getCorrectAnswer()))
                || (accepted != null && accepted.stream().map(GrammarAnswerNormalizer::normalize)
                        .anyMatch(entered::equals));
        return answer.answerPreview(preview(request.answer())).correct(matched).build();
    }

    private static String preview(String answer) {
        if (answer.codePointCount(0, answer.length()) <= ANSWER_PREVIEW_LIMIT) {
            return answer;
        }
        int end = answer.offsetByCodePoints(0, ANSWER_PREVIEW_LIMIT - 1);
        return answer.substring(0, end) + "…";
    }

    private static List<Integer> optionOrder(GrammarQuestionVo question, boolean shuffle) {
        if (question.getType() != GrammarQuestionType.MULTIPLE_CHOICE) {
            return List.of();
        }
        var order = new ArrayList<>(IntStream.range(0, question.getOptions().size()).boxed().toList());
        if (shuffle) {
            Collections.shuffle(order);
        }
        return List.copyOf(order);
    }
}
