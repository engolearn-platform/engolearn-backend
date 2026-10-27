package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.repository.LearningPathLifecycleRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LearningPathLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathLifecycleService.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_TRANSITIONS_PER_PATH = 100;

    private final LearningPathLifecycleRepository repository;
    private final LearningPathQuestionRepository questions;
    private final LearningPathLifecycleEngine engine = new LearningPathLifecycleEngine();

    public LearningPathLifecycleService(LearningPathLifecycleRepository repository,
                                        LearningPathQuestionRepository questions) {
        this.repository = repository;
        this.questions = questions;
    }

    public synchronized void processDue(Instant now) {
        List<ObjectId> failedIds = new ArrayList<>();
        while (true) {
            List<LearningPath> due = repository.findDue(now, BATCH_SIZE, failedIds);
            if (due.isEmpty()) return;
            for (LearningPath path : due) {
                try {
                    processPath(path.getId(), now);
                } catch (RuntimeException exception) {
                    failedIds.add(path.getId());
                    log.error("Cannot advance Learning Path {}", path.getId(), exception);
                }
            }
        }
    }

    private void processPath(ObjectId id, Instant now) {
        for (int attempt = 0; attempt < MAX_TRANSITIONS_PER_PATH; attempt++) {
            LearningPath path = repository.findById(id).orElse(null);
            if (path == null || !Boolean.TRUE.equals(path.getActive())
                    || path.getNextRunAt() == null
                    || path.getNextRunAt().toInstant().isAfter(now)) {
                return;
            }
            String expectedStatus = path.getStatus();
            Date expectedNextRunAt = path.getNextRunAt();
            Integer expectedAnswered = path.getProgress() == null
                    ? null : path.getProgress().getAnswered();
            Map<String, Object> changes = engine.advance(path, now, answerKeys(path));
            if (changes.size() == 1 && expectedNextRunAt.equals(changes.get("next_run_at"))) {
                throw new IllegalStateException("Learning Path deadline did not advance: " + id);
            }
            repository.apply(id, expectedStatus, expectedNextRunAt, expectedAnswered, changes);
        }
        throw new IllegalStateException("Learning Path exceeded transition limit: " + id);
    }

    private Map<String, String> answerKeys(LearningPath path) {
        List<String> ids = path.getWeeks().stream()
                .filter(week -> "OPEN".equals(week.getStatus()))
                .flatMap(week -> week.getDays().stream())
                .filter(day -> "IN_PROGRESS".equals(day.getStatus()))
                .flatMap(day -> day.getItems().stream())
                .filter(item -> item.getDraft() != null && item.getAnswer() == null)
                .map(LearningPathDayItem::getQuestionId)
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<String, String> keys = new HashMap<>();
        for (Document snapshot : questions.findAnswerKeys(path.getId(), path.getUserId(), ids)) {
            String id = snapshot.getString("question_id");
            String key = snapshot.getString("correct_option_id");
            if (id == null || key == null || keys.putIfAbsent(id, key) != null) {
                throw new IllegalStateException("Invalid Learning Path answer key for " + path.getId());
            }
        }
        if (ids.stream().anyMatch(id -> !keys.containsKey(id))) {
            throw new IllegalStateException("Missing Learning Path answer key for " + path.getId());
        }
        return keys;
    }
}
