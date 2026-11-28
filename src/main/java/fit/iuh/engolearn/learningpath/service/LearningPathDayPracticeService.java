package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;

import fit.iuh.engolearn.learningpath.dto.response.StartLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathAudioResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayAvailability;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@lombok.RequiredArgsConstructor
public class LearningPathDayPracticeService {
    private final MicroSkillLookup microSkills;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_START_ATTEMPTS = 3;

    private final LearningPathRepository paths;
    private final LearningPathPracticeRepository practice;
    private final LearningPathQuestionRepository questions;
    private final LearningPathAudioService audio;
    private final Clock clock;
    private final CurrentUserProvider currentUser;


    public StartLearningPathDayResponse start(String pathId, int weekNo, int dayNo, String userId) {
        ObjectId pathObjectId = new ObjectId(pathId);

        for (int attempt = 0; attempt < MAX_START_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            LearningPath path = paths.findByIdAndUserId(pathObjectId, userId)
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
            ensureAvailable(path, week, day, now);

            List<LearningPathDayItem> items = day.getItems().stream()
                    .sorted(Comparator.comparing(LearningPathDayItem::getOrder))
                    .toList();
            if (path.getConfig() == null || path.getConfig().getQuestionsPerDay() == null
                    || items.size() != path.getConfig().getQuestionsPerDay()
                    || items.size() != new HashSet<>(items.stream()
                    .map(LearningPathDayItem::getQuestionId).toList()).size()) {
                throw new IllegalStateException("Learning Path day has missing or duplicate questions");
            }
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).getOrder() == null || items.get(index).getOrder() != index + 1) {
                    throw new IllegalStateException("Learning Path day has invalid question order");
                }
            }
            Map<String, Document> snapshots = loadSnapshots(pathObjectId, userId, items);
            List<StartLearningPathDayResponse.Question> questionInfos = items.stream()
                    .map(item -> question(item, snapshots.get(item.getQuestionId())))
                    .toList();

            if (LearningPathDayStatus.NOT_STARTED == day.getStatus()) {
                LocalDate startedDate = LocalDate.ofInstant(now, ZONE);
                Date startedAt = Date.from(now);
                Date autoCloseAt = Date.from(startedDate.plusDays(1).atStartOfDay(ZONE).toInstant());
                Date nextRunAt = path.getNextRunAt().before(autoCloseAt)
                        ? path.getNextRunAt() : autoCloseAt;
                boolean started = practice.startDay(pathObjectId, userId, weekNo, dayNo,
                        path.getStatus().name(), path.getRevision(), path.getNextRunAt(), startedAt, startedDate.toString(),
                        autoCloseAt, nextRunAt);
                if (!started) continue;
                return response(pathId, weekNo, dayNo, startedAt, autoCloseAt, questionInfos);
            }
            return response(pathId, weekNo, dayNo, day.getStartedAt(), day.getAutoCloseAt(),
                    questionInfos);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ngày luyện tập vừa thay đổi, vui lòng thử lại");
    }

    @PreAuthorize("isAuthenticated()")
    public LearningPathAudioResponse getAudio(String pathId, int weekNo, int dayNo, String questionId) {
        String userId = currentUser.requireCurrentUserId();
        ObjectId id = new ObjectId(pathId);
        LearningPath path = paths.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Learning Path"));
        if (path.getStatus() == LearningPathStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Learning Path đã bị hủy và không thể mở");
        }
        LearningPathWeek week = path.getWeeks().stream().filter(value -> value.getWeekNo() == weekNo).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tuần luyện tập"));
        LearningPathDay day = week.getDays().stream().filter(value -> value.getDayNo() == dayNo).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ngày luyện tập"));
        if (day.getStatus() == LearningPathDayStatus.IN_PROGRESS) {
            ensureAvailable(path, week, day, clock.instant());
        } else if (day.getStatus() != LearningPathDayStatus.COMPLETED && day.getStatus() != LearningPathDayStatus.SUBMITTED
                && day.getStatus() != LearningPathDayStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ngày luyện tập chưa bắt đầu");
        }
        if (day.getItems() == null || day.getItems().stream().anyMatch(item -> item == null || item.getQuestionId() == null)) {
            throw new IllegalStateException("Learning Path day questions are invalid");
        }
        var matching = day.getItems().stream().filter(item -> questionId.equals(item.getQuestionId())).toList();
        if (matching.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi trong ngày luyện tập");
        if (matching.size() != 1) throw new IllegalStateException("Duplicate Learning Path day question");
        var saved = questions.findSnapshots(id, userId, List.of(questionId));
        if (saved.size() != 1 || !questionId.equals(saved.getFirst().getString("question_id"))
                || matching.getFirst().getMicroSkill() == null
                || !matching.getFirst().getMicroSkill().equals(saved.getFirst().getString("micro_skill"))) {
            throw new IllegalStateException("Learning Path question snapshot is incomplete or inconsistent");
        }
        var delivery = audio.resolve(saved.getFirst().get("passage", Document.class));
        if (delivery == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Câu hỏi không có audio");
        return delivery;
    }

    private void ensureAvailable(LearningPath path, LearningPathWeek week,
                                 LearningPathDay day, Instant now) {
        if (!LearningPathDayAvailability.available(path, week, day, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ngày luyện tập không còn khả dụng");
        }
    }

    private Map<String, Document> loadSnapshots(ObjectId pathId, String userId,
                                                 List<LearningPathDayItem> items) {
        List<String> ids = items.stream().map(LearningPathDayItem::getQuestionId).toList();
        Map<String, Document> snapshots = new HashMap<>();
        for (Document snapshot : questions.findSnapshots(pathId, userId, ids)) {
            if (snapshots.putIfAbsent(snapshot.getString("question_id"), snapshot) != null) {
                throw new IllegalStateException("Duplicate Learning Path question snapshot");
            }
        }
        if (snapshots.size() != ids.size() || !snapshots.keySet().containsAll(ids)) {
            throw new IllegalStateException("Learning Path question snapshot is incomplete");
        }
        return snapshots;
    }

    private StartLearningPathDayResponse response(String pathId, int weekNo, int dayNo,
                                                  Date startedAt, Date autoCloseAt,
                                                  List<StartLearningPathDayResponse.Question> questions) {
        return new StartLearningPathDayResponse(pathId, weekNo, dayNo,
                "IN_PROGRESS", startedAt, autoCloseAt, questions, this.microSkills.list());
    }

    private StartLearningPathDayResponse.Question question(LearningPathDayItem item,
                                                            Document snapshot) {
        if (snapshot.getString("prompt") == null || snapshot.getString("skill") == null
                || !item.getMicroSkill().equals(snapshot.getString("micro_skill"))) {
            throw new IllegalStateException("Learning Path question snapshot is invalid");
        }
        Document passage = snapshot.get("passage", Document.class);
        StartLearningPathDayResponse.Passage passageInfo = null;
        if (passage != null) {
            passageInfo = new StartLearningPathDayResponse.Passage(
                    passage.getString("title"), passage.getString("text"), audio.resolve(passage));
        }
        List<Document> savedOptions = snapshot.getList("options", Document.class);
        if (savedOptions == null || savedOptions.size() != 4) {
            throw new IllegalStateException("Learning Path question options are incomplete");
        }
        List<StartLearningPathDayResponse.Option> options = savedOptions.stream()
                .map(option -> new StartLearningPathDayResponse.Option(
                        option.getString("option_id"), option.getString("text")))
                .toList();
        return new StartLearningPathDayResponse.Question(item.getOrder(), item.getQuestionId(),
                item.getMicroSkill(), snapshot.getString("skill"), passageInfo,
                snapshot.getString("prompt"), options,
                item.getDraft() == null ? null : item.getDraft().getSelectedOptionId());
    }
}
