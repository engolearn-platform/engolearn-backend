package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;

import fit.iuh.engolearn.learningpath.dto.request.SaveLearningPathAnswerDraftRequest;
import fit.iuh.engolearn.learningpath.dto.response.SaveLearningPathAnswerDraftResponse;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDraft;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningPathAnswerDraftService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_SAVE_ATTEMPTS = 3;

    private final LearningPathRepository paths;
    private final LearningPathPracticeRepository practice;
    private final LearningPathQuestionRepository questions;
    private final Clock clock;

    public LearningPathAnswerDraftService(LearningPathRepository paths,
                                          LearningPathPracticeRepository practice,
                                          LearningPathQuestionRepository questions,
                                          Clock clock) {
        this.paths = paths;
        this.practice = practice;
        this.questions = questions;
        this.clock = clock;
    }

    public SaveLearningPathAnswerDraftResponse save(String pathId, int weekNo, int dayNo,
                                                     String questionId, String userId,
                                                     SaveLearningPathAnswerDraftRequest request) {
        ObjectId pathObjectId = new ObjectId(pathId);
        ObjectId userObjectId = new ObjectId(userId);

        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            LearningPath path = paths.findByIdAndUserId(pathObjectId, userObjectId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy Learning Path"));
            LearningPathWeek week = path.getWeeks().stream()
                    .filter(item -> item.getWeekNo() == weekNo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy tuần luyện tập"));
            LearningPathDay day = week.getDays().stream()
                    .filter(item -> item.getDayNo() == dayNo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy ngày luyện tập"));
            LearningPathDayItem item = day.getItems().stream()
                    .filter(question -> questionId.equals(question.getQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi trong ngày"));
            ensureEditable(path, week, day, item, now);
            ensureOptionExists(pathObjectId, userObjectId, questionId, request.selectedOptionId());

            LocalDate activityDate = LocalDate.ofInstant(now, ZONE);
            Date savedAt = Date.from(now);
            Date cancelAt = Date.from(activityDate.plusDays(15).atStartOfDay(ZONE).toInstant());
            Date nextRunAt = earlier(week.getCloseAt(), cancelAt);
            for (LearningPathDay scheduledDay : week.getDays()) {
                if (LearningPathDayStatus.IN_PROGRESS == scheduledDay.getStatus()
                        && scheduledDay.getAutoCloseAt() != null) {
                    nextRunAt = earlier(nextRunAt, scheduledDay.getAutoCloseAt());
                }
            }
            LearningPathDraft draft = new LearningPathDraft();
            draft.setSelectedOptionId(request.selectedOptionId());
            draft.setSavedAt(savedAt);
            if (practice.saveDraft(pathObjectId, userObjectId, weekNo, dayNo,
                    questionId, path.getRevision(), path.getNextRunAt(), savedAt, draft,
                    activityDate.toString(), cancelAt, nextRunAt)) {
                return new SaveLearningPathAnswerDraftResponse(
                        questionId, request.selectedOptionId(), savedAt);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Bài luyện tập vừa thay đổi, vui lòng thử lại");
    }

    private void ensureEditable(LearningPath path, LearningPathWeek week,
                                LearningPathDay day, LearningPathDayItem item, Instant now) {
        if (!Boolean.TRUE.equals(path.getActive()) || LearningPathStatus.IN_PROGRESS != path.getStatus()
                || path.getNextRunAt() == null || !path.getNextRunAt().toInstant().isAfter(now)
                || path.getInactivity() == null || path.getInactivity().getCancelAt() == null
                || !path.getInactivity().getCancelAt().toInstant().isAfter(now)
                || LearningPathWeekStatus.OPEN != week.getStatus()
                || week.getCloseAt() == null || !week.getCloseAt().toInstant().isAfter(now)
                || LearningPathDayStatus.IN_PROGRESS != day.getStatus()
                || day.getAutoCloseAt() == null || !day.getAutoCloseAt().toInstant().isAfter(now)
                || item.getAnswer() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Câu hỏi không còn cho phép sửa đáp án");
        }
    }

    private void ensureOptionExists(ObjectId pathId, ObjectId userId,
                                    String questionId, String selectedOptionId) {
        List<Document> snapshots = questions.findSnapshots(pathId, userId, List.of(questionId));
        if (snapshots.size() != 1) {
            throw new IllegalStateException("Learning Path question snapshot is missing");
        }
        List<Document> options = snapshots.getFirst().getList("options", Document.class);
        if (options == null || options.stream()
                .noneMatch(option -> selectedOptionId.equals(option.getString("option_id")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đáp án được chọn không tồn tại");
        }
    }

    private Date earlier(Date first, Date second) {
        return first.before(second) ? first : second;
    }
}
