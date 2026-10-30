package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicAttemptResponse;
import fit.iuh.engolearn.topic.dto.response.TopicCatalogPolicy;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionPurposeResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TopicAttemptMapper {
    public TopicAttemptResponse toRetryResponse(UserTopicItemAttempt attempt, TopicItemVersion version) {
        var position = attempt.getFurthestReached();
        return toRetryResponse(attempt, version,
                position == null ? null : position.getStage(), position == null ? 0 : position.getItemIndex(),
                attempt.getVersion() == null ? 0 : attempt.getVersion(), attempt.getLastAccessedAt());
    }

    public TopicAttemptResponse toRetryResponse(UserTopicItemAttempt attempt, TopicItemVersion version,
            LearningStage stage, int index, long revision, Instant accessed) {
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version);
        List<TopicCatalogPolicy.Position> positions = positions(content);
        int fill = quizCount(version, QuizType.FILL_BLANK);
        int totalQuiz = fill + quizCount(version, QuizType.SITUATIONAL_CHOICE);
        int completed = positions.indexOf(new TopicCatalogPolicy.Position(
                stage == null ? "CONTEXT" : stage.name(), index));
        if (stage == null && attempt.getStatus() == AttemptStatus.COMPLETED) {
            completed = positions.size() + totalQuiz;
        }
        if (completed < 0) {
            completed = positions.size() + (stage == LearningStage.SITUATIONAL_CHOICE ? fill : 0) + index;
        }

        int words = 0;
        int expressions = 0;
        List<TopicExpressionPurposeResponse> purposes = content.getExpressions().stream()
                .flatMap(group -> group.getPurposes().stream()).toList();
        for (int i = 0; i < Math.min(completed, positions.size()); i++) {
            TopicCatalogPolicy.Position position = positions.get(i);
            if (position.stage().equals("VOCAB")) {
                words++;
            }
            if (position.stage().equals("EXPRESSIONS")) {
                expressions += purposes.get(position.itemIndex()).getExamples().size();
            }
        }

        int total = positions.size() + totalQuiz;
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * completed / total);
        TopicAttemptResponse.Progress progress = new TopicAttemptResponse.Progress(
                completed, total, percent, words, expressions, Math.max(0, completed - positions.size()), totalQuiz);
        return new TopicAttemptResponse(
                attempt.getId(), attempt.getTopicId(), attempt.getTopicItemId(), version.getId(), version.getVersion(),
                "RETRY", stage == null ? attempt.getStatus().name() : "IN_PROGRESS", revision,
                stage == null ? null : new TopicCatalogPolicy.Position(stage.name(), index), progress,
                attempt.getUserTopicAttemptId(), attempt.getStartedAt(), accessed,
                stage == null ? attempt.getCompletedAt() : null);
    }

    public List<TopicCatalogPolicy.Position> positions(TopicItemContentResponse content) {
        List<TopicCatalogPolicy.Position> positions = new ArrayList<>();
        positions.add(new TopicCatalogPolicy.Position("CONTEXT", 0));
        for (int i = 0; i < content.getVocabularies().size(); i++) {
            positions.add(new TopicCatalogPolicy.Position("VOCAB", i));
        }
        int purposeCount = content.getExpressions().stream().mapToInt(group -> group.getPurposes().size()).sum();
        for (int i = 0; i < purposeCount; i++) {
            positions.add(new TopicCatalogPolicy.Position("EXPRESSIONS", i));
        }
        return positions;
    }

    private int quizCount(TopicItemVersion version, QuizType type) {
        if (version.getQuizzes() == null) {
            return 0;
        }
        return version.getQuizzes().stream().filter(module -> module.getQuizType() == type)
                .mapToInt(module -> module.getQuestions() == null ? 0 : module.getQuestions().size()).sum();
    }
}
