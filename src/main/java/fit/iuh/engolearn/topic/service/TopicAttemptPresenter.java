package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class TopicAttemptPresenter {
    private final TopicItemVersionRepository versions;
    public TopicAttemptPresenter(TopicItemVersionRepository versions) { this.versions = versions; }

    public TopicAttemptResponse describe(UserTopicItemAttempt attempt) {
        var p = attempt.getFurthestReached();
        return describe(attempt, p == null ? null : p.getStage(), p == null ? 0 : p.getItemIndex(),
                attempt.getVersion() == null ? 0 : attempt.getVersion(), attempt.getLastAccessedAt());
    }

    public TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed) {
        TopicItemVersion version = versions.findById(attempt.getTopicItemVersionId()).orElseThrow();
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version);
        List<TopicCatalogPolicy.Position> positions = positions(content);
        int fill = count(version, QuizType.FILL_BLANK), totalQuiz = fill + count(version, QuizType.SITUATIONAL_CHOICE);
        int completed = stage == null && attempt.getStatus() == AttemptStatus.COMPLETED ? positions.size() + totalQuiz :
                positions.indexOf(new TopicCatalogPolicy.Position(stage == null ? "CONTEXT" : stage.name(), index));
        if (completed < 0) completed = positions.size() + (stage == LearningStage.SITUATIONAL_CHOICE ? fill : 0) + index;
        int words = 0, expressions = 0;
        List<TopicExpressionPurposeResponse> purposes = content.getExpressions().stream().flatMap(g -> g.getPurposes().stream()).toList();
        for (int i = 0; i < Math.min(completed, positions.size()); i++) {
            var p = positions.get(i);
            if (p.stage().equals("VOCAB")) words++;
            if (p.stage().equals("EXPRESSIONS")) expressions += purposes.get(p.itemIndex()).getExamples().size();
        }
        int total = positions.size() + totalQuiz;
        return new TopicAttemptResponse(attempt.getId(), attempt.getTopicId(), attempt.getTopicItemId(), version.getId(), version.getVersion(),
                "RETRY", stage == null ? attempt.getStatus().name() : "IN_PROGRESS", revision,
                stage == null ? null : new TopicCatalogPolicy.Position(stage.name(),index),
                new TopicAttemptResponse.Progress(completed,total,total == 0 ? 0 : (int)Math.round(100.0*completed/total),
                        words,expressions,Math.max(0,completed-positions.size()),totalQuiz),
                attempt.getUserTopicAttemptId(),attempt.getStartedAt(),accessed,stage == null ? attempt.getCompletedAt() : null);
    }

    public List<TopicCatalogPolicy.Position> positions(TopicItemContentResponse content) {
        List<TopicCatalogPolicy.Position> values = new ArrayList<>();
        values.add(new TopicCatalogPolicy.Position("CONTEXT",0));
        for (int i=0;i<content.getVocabularies().size();i++) values.add(new TopicCatalogPolicy.Position("VOCAB",i));
        int size=content.getExpressions().stream().mapToInt(g->g.getPurposes().size()).sum();
        for (int i=0;i<size;i++) values.add(new TopicCatalogPolicy.Position("EXPRESSIONS",i));
        return values;
    }
    private int count(TopicItemVersion version, QuizType type) {
        return version.getQuizzes()==null?0:version.getQuizzes().stream().filter(m->m.getQuizType()==type)
                .mapToInt(m->m.getQuestions()==null?0:m.getQuestions().size()).sum();
    }
}
