package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.QuizFeedbackResponse;
import fit.iuh.engolearn.topic.dto.response.QuizFillBlankResponse;
import fit.iuh.engolearn.topic.dto.response.QuizOptionResponse;
import fit.iuh.engolearn.topic.dto.response.QuizResponse;
import fit.iuh.engolearn.topic.dto.response.QuizSituationalChoiceResponse;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.QuizOptionVo;
import fit.iuh.engolearn.topic.model.vo.QuizQuestionVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TopicQuizMapper {

    public record QuizEntry(String quizId, QuizType type, int indexInType, int totalInType,
                            QuizModuleVo module, QuizQuestionVo question, int questionIndexInModule) {
    }

    private record ModuleRef(int arrayIndex, QuizModuleVo module) {
    }

    private record RawEntry(String quizId, int indexInType, ModuleRef ref,
                            QuizQuestionVo question, int questionIndex) {
    }

    public List<QuizEntry> sequence(TopicItemVersion version) {
        List<QuizModuleVo> quizzes = version.getQuizzes() == null ? List.of() : version.getQuizzes();
        List<QuizEntry> entries = new ArrayList<>();
        Set<String> seenQuizIds = new HashSet<>();
        for (QuizType type : List.of(QuizType.FILL_BLANK, QuizType.SITUATIONAL_CHOICE)) {
            List<ModuleRef> modules = new ArrayList<>();
            for (int arrayIndex = 0; arrayIndex < quizzes.size(); arrayIndex++) {
                QuizModuleVo module = quizzes.get(arrayIndex);
                if (module != null && module.getQuizType() == type) modules.add(new ModuleRef(arrayIndex, module));
            }
            modules.sort(Comparator.comparingInt((ModuleRef ref) ->
                    ref.module().getOrderIndex() != null ? ref.module().getOrderIndex() : ref.arrayIndex()));
            List<RawEntry> raw = new ArrayList<>();
            for (ModuleRef ref : modules) {
                int moduleKey = ref.module().getOrderIndex() != null ? ref.module().getOrderIndex() : ref.arrayIndex();
                List<QuizQuestionVo> questions = ref.module().getQuestions() == null
                        ? List.of() : ref.module().getQuestions();
                for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                    QuizQuestionVo question = questions.get(questionIndex);
                    if (question == null) continue;
                    String quizId = version.getId() + ":quiz:" + moduleKey + ":" + questionIndex;
                    raw.add(new RawEntry(quizId, raw.size(), ref, question, questionIndex));
                }
            }
            for (RawEntry rawEntry : raw) {
                if (!seenQuizIds.add(rawEntry.quizId())) {
                    throw unavailable("duplicate quizId", rawEntry.quizId());
                }
                entries.add(new QuizEntry(rawEntry.quizId(), type, rawEntry.indexInType(), raw.size(),
                        rawEntry.ref().module(), rawEntry.question(), rawEntry.questionIndex()));
            }
        }
        return List.copyOf(entries);
    }

    public Optional<QuizEntry> find(List<QuizEntry> sequence, String quizId) {
        return sequence.stream().filter(entry -> entry.quizId().equals(quizId)).findFirst();
    }

    public QuizResponse toDto(QuizEntry entry) {
        String title = entry.module().getTitle();
        if (isBlank(title)) throw unavailable("module.title", entry.quizId());
        requireExplanation(entry);
        List<QuizOptionResponse> options = options(entry);
        String situationVi = situationVi(entry.question());
        String tips = entry.question().getTips();
        String promptAudioUrl = entry.question().getAudioUrl();
        if (entry.type() == QuizType.FILL_BLANK) {
            String[] parts = splitAtAnswer(entry);
            return new QuizFillBlankResponse(entry.quizId(), title, entry.indexInType(), entry.totalInType(),
                    situationVi, null, tips, promptAudioUrl, "FILL_BLANK", parts[0], parts[1], options);
        }
        if (entry.type() == QuizType.SITUATIONAL_CHOICE) {
            if (isBlank(entry.question().getPrompt())) throw unavailable("question.prompt", entry.quizId());
            return new QuizSituationalChoiceResponse(entry.quizId(), title, entry.indexInType(), entry.totalInType(),
                    situationVi, null, tips, promptAudioUrl, "SITUATIONAL_CHOICE",
                    entry.question().getPrompt(), options);
        }
        throw new IllegalStateException("Unsupported quiz type " + entry.type());
    }

    public QuizFeedbackResponse feedback(QuizEntry entry) {
        requireExplanation(entry);
        QuizSentenceVo sentence = entry.question().getSentence();
        String completedSentenceEn = entry.type() == QuizType.SITUATIONAL_CHOICE || sentence == null
                ? null : sentence.getTextEn();
        String translationVi = sentence == null ? null : sentence.getMeaningVi();
        String audioUrl = sentence == null ? null : sentence.getAudioUrl();
        return new QuizFeedbackResponse(correctOptionId(entry), completedSentenceEn, translationVi,
                entry.question().getExplanation(), null, audioUrl);
    }

    public String correctOptionId(QuizEntry entry) {
        return entry.quizId() + ":option:" + correctIndex(entry);
    }

    private String[] splitAtAnswer(QuizEntry entry) {
        QuizSentenceVo sentence = entry.question().getSentence();
        if (sentence == null || isBlank(sentence.getTextEn())) throw unavailable("sentence.textEn", entry.quizId());
        String text = sentence.getTextEn();
        String answer = optionsOf(entry).get(correctIndex(entry)).getTextEn();
        if (isBlank(answer)) throw unavailable("correct option textEn", entry.quizId());
        int occurrences = 0;
        for (int index = text.indexOf(answer); index >= 0; index = text.indexOf(answer, index + 1)) occurrences++;
        if (occurrences != 1) {
            throw unavailable("sentence.textEn occurrence count of correct answer = " + occurrences, entry.quizId());
        }
        int start = text.indexOf(answer);
        return new String[]{text.substring(0, start), text.substring(start + answer.length())};
    }

    private List<QuizOptionResponse> options(QuizEntry entry) {
        List<QuizOptionVo> options = optionsOf(entry);
        if (entry.type() == QuizType.FILL_BLANK) {
            if (options.size() < 4 || options.size() > 6) {
                throw unavailable("FILL_BLANK option count = " + options.size(), entry.quizId());
            }
            for (QuizOptionVo option : options) {
                if (option == null || isBlank(option.getTextEn())) {
                    throw unavailable("FILL_BLANK option.textEn", entry.quizId());
                }
            }
        } else if (entry.type() == QuizType.SITUATIONAL_CHOICE) {
            if (options.size() != 4) {
                throw unavailable("SITUATIONAL_CHOICE option count = " + options.size(), entry.quizId());
            }
        }
        correctIndex(entry);
        List<QuizOptionResponse> mapped = new ArrayList<>(options.size());
        for (int position = 0; position < options.size(); position++) {
            QuizOptionVo option = options.get(position);
            if (option == null) throw unavailable("option at position " + position, entry.quizId());
            mapped.add(new QuizOptionResponse(entry.quizId() + ":option:" + position, option.getTextEn()));
        }
        return List.copyOf(mapped);
    }

    private int correctIndex(QuizEntry entry) {
        List<QuizOptionVo> options = optionsOf(entry);
        int correct = -1;
        int count = 0;
        for (int position = 0; position < options.size(); position++) {
            QuizOptionVo option = options.get(position);
            if (option != null && Boolean.TRUE.equals(option.getIsCorrect())) {
                correct = position;
                count++;
            }
        }
        if (count != 1) throw unavailable("correct option count = " + count, entry.quizId());
        return correct;
    }

    private List<QuizOptionVo> optionsOf(QuizEntry entry) {
        return entry.question().getOptions() == null ? List.of() : entry.question().getOptions();
    }

    private void requireExplanation(QuizEntry entry) {
        if (isBlank(entry.question().getExplanation())) throw unavailable("explanation", entry.quizId());
    }

    private String situationVi(QuizQuestionVo question) {
        if (question.getQuizData() == null) return null;
        Object value = question.getQuizData().get("scenario_vi");
        return value instanceof String scenario ? scenario : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static TopicRequestException unavailable(String field, String quizId) {
        return new TopicRequestException(404, "CONTENT_UNAVAILABLE", field + " for quiz " + quizId);
    }
}
