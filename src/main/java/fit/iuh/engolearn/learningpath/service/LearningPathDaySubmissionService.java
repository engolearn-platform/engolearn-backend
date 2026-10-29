package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.SubmitLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningPathDaySubmissionService {
    private final LearningPathRepository paths;
    private final LearningPathPracticeRepository practice;
    private final LearningPathQuestionRepository questions;
    private final Clock clock;
    private final LearningPathLifecycleEngine engine = new LearningPathLifecycleEngine();

    public LearningPathDaySubmissionService(LearningPathRepository paths, LearningPathPracticeRepository practice,
                                            LearningPathQuestionRepository questions, Clock clock) {
        this.paths = paths;
        this.practice = practice;
        this.questions = questions;
        this.clock = clock;
    }

    public SubmitLearningPathDayResponse submit(String pathId, int weekNo, int dayNo, String userId) {
        ObjectId pathObjectId = new ObjectId(pathId);
        ObjectId userObjectId = new ObjectId(userId);
        for (int attempt = 0; attempt < 3; attempt++) {
            LearningPath path = paths.findByIdAndUserId(pathObjectId, userObjectId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Learning Path"));
            LearningPathWeek week = path.getWeeks().stream().filter(w -> w.getWeekNo() == weekNo).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tuần luyện tập"));
            LearningPathDay day = week.getDays().stream().filter(d -> d.getDayNo() == dayNo).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ngày luyện tập"));
            Instant now = clock.instant();
            ensureAvailable(path, week, day, now);
            if (day.getItems() == null || day.getItems().size() != 10) {
                throw new IllegalStateException("Learning Path day has invalid questions");
            }
            List<String> ids = day.getItems().stream().filter(item -> item.getDraft() != null && item.getAnswer() == null)
                    .map(LearningPathDayItem::getQuestionId).toList();
            Map<String, String> keys = new HashMap<>();
            if (!ids.isEmpty()) {
                for (Document snapshot : questions.findAnswerKeys(pathObjectId, userObjectId, ids)) {
                    String id = snapshot.getString("question_id");
                    String key = snapshot.getString("correct_option_id");
                    if (id == null || key == null || keys.putIfAbsent(id, key) != null) {
                        throw new IllegalStateException("Invalid Learning Path answer key");
                    }
                }
                if (ids.stream().anyMatch(id -> !keys.containsKey(id))) {
                    throw new IllegalStateException("Missing Learning Path answer key");
                }
            }
            Map<String, Object> changes = engine.submit(path, path.getWeeks().indexOf(week),
                    week.getDays().indexOf(day), now, keys);
            if (practice.submit(pathObjectId, userObjectId, weekNo, dayNo, path.getRevision(),
                    Date.from(clock.instant()), changes)) {
                return new SubmitLearningPathDayResponse(pathId, weekNo, dayNo, day.getStatus().name(),
                        day.getClosedAt(), day.getProgress().getAssigned(), day.getProgress().getAnswered(),
                        day.getProgress().getCorrect(), day.getResult().getWrong(),
                        day.getResult().getCompletionRate(), day.getResult().getPracticeScore());
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài luyện tập vừa thay đổi, vui lòng thử lại");
    }

    private void ensureAvailable(LearningPath path, LearningPathWeek week, LearningPathDay day, Instant now) {
        if (!Boolean.TRUE.equals(path.getActive()) || path.getStatus() != LearningPathStatus.IN_PROGRESS
                || path.getNextRunAt() == null || !path.getNextRunAt().toInstant().isAfter(now)
                || path.getInactivity() == null || path.getInactivity().getCancelAt() == null
                || !path.getInactivity().getCancelAt().toInstant().isAfter(now)
                || week.getStatus() != LearningPathWeekStatus.OPEN || week.getCloseAt() == null
                || !week.getCloseAt().toInstant().isAfter(now)
                || day.getStatus() != LearningPathDayStatus.IN_PROGRESS || day.getAutoCloseAt() == null
                || !day.getAutoCloseAt().toInstant().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ngày luyện tập không còn khả dụng");
        }
    }
}
