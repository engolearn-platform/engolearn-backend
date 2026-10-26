package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.StartLearningPathDayResponse;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

@Service
public class LearningPathDayPracticeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_START_ATTEMPTS = 3;

    private final LearningPathRepository paths;
    private final LearningPathPracticeRepository practice;
    private final LearningPathQuestionRepository questions;
    private final Clock clock;

    public LearningPathDayPracticeService(LearningPathRepository paths,
                                          LearningPathPracticeRepository practice,
                                          LearningPathQuestionRepository questions,
                                          Clock clock) {
        this.paths = paths;
        this.practice = practice;
        this.questions = questions;
        this.clock = clock;
    }

    public StartLearningPathDayResponse start(String pathId, int weekNo, int dayNo, String userId) {
        ObjectId pathObjectId = new ObjectId(pathId);
        ObjectId userObjectId = new ObjectId(userId);

        for (int attempt = 0; attempt < MAX_START_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            LearningPath path = paths.findByIdAndUserId(pathObjectId, userObjectId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy Learning Path"));
            LearningPath.Week week = path.getWeeks().stream()
                    .filter(item -> item.getWeekNo() == weekNo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy tuần luyện tập"));
            LearningPath.Day day = week.getDays().stream()
                    .filter(item -> item.getDayNo() == dayNo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy ngày luyện tập"));
            ensureAvailable(path, week, day, now);

            List<LearningPath.DayItem> items = day.getItems().stream()
                    .sorted(Comparator.comparing(LearningPath.DayItem::getOrder))
                    .toList();
            if (path.getConfig() == null || path.getConfig().getQuestionsPerDay() == null
                    || items.size() != path.getConfig().getQuestionsPerDay()
                    || items.size() != new HashSet<>(items.stream()
                    .map(LearningPath.DayItem::getQuestionId).toList()).size()) {
                throw new IllegalStateException("Learning Path day has missing or duplicate questions");
            }
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).getOrder() == null || items.get(index).getOrder() != index + 1) {
                    throw new IllegalStateException("Learning Path day has invalid question order");
                }
            }
            Map<String, Document> snapshots = loadSnapshots(pathObjectId, userObjectId, items);
            List<StartLearningPathDayResponse.Question> questionInfos = items.stream()
                    .map(item -> question(item, snapshots.get(item.getQuestionId())))
                    .toList();

            if ("NOT_STARTED".equals(day.getStatus())) {
                LocalDate startedDate = LocalDate.ofInstant(now, ZONE);
                Date startedAt = Date.from(now);
                Date autoCloseAt = Date.from(startedDate.plusDays(1).atStartOfDay(ZONE).toInstant());
                Date nextRunAt = path.getNextRunAt().before(autoCloseAt)
                        ? path.getNextRunAt() : autoCloseAt;
                boolean started = practice.startDay(pathObjectId, userObjectId, weekNo, dayNo,
                        path.getStatus(), path.getNextRunAt(), startedAt, startedDate.toString(),
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

    private void ensureAvailable(LearningPath path, LearningPath.Week week,
                                 LearningPath.Day day, Instant now) {
        if (!Boolean.TRUE.equals(path.getActive())
                || !("NOT_STARTED".equals(path.getStatus()) || "IN_PROGRESS".equals(path.getStatus()))
                || path.getNextRunAt() == null || !path.getNextRunAt().toInstant().isAfter(now)
                || path.getInactivity() == null || path.getInactivity().getCancelAt() == null
                || !path.getInactivity().getCancelAt().toInstant().isAfter(now)
                || !"OPEN".equals(week.getStatus())
                || week.getCloseAt() == null || !week.getCloseAt().toInstant().isAfter(now)
                || !("NOT_STARTED".equals(day.getStatus()) || "IN_PROGRESS".equals(day.getStatus()))
                || ("IN_PROGRESS".equals(day.getStatus())
                && (day.getAutoCloseAt() == null || !day.getAutoCloseAt().toInstant().isAfter(now)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ngày luyện tập không còn khả dụng");
        }
    }

    private Map<String, Document> loadSnapshots(ObjectId pathId, ObjectId userId,
                                                 List<LearningPath.DayItem> items) {
        List<String> ids = items.stream().map(LearningPath.DayItem::getQuestionId).toList();
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
                "IN_PROGRESS", startedAt, autoCloseAt, questions);
    }

    private StartLearningPathDayResponse.Question question(LearningPath.DayItem item,
                                                            Document snapshot) {
        if (snapshot.getString("prompt") == null || snapshot.getString("skill") == null
                || !item.getMicroSkill().equals(snapshot.getString("micro_skill"))) {
            throw new IllegalStateException("Learning Path question snapshot is invalid");
        }
        Document passage = snapshot.get("passage", Document.class);
        StartLearningPathDayResponse.Passage passageInfo = null;
        if (passage != null) {
            Document audio = passage.get("audio", Document.class);
            StartLearningPathDayResponse.Audio audioInfo = audio == null ? null
                    : new StartLearningPathDayResponse.Audio(audio.getString("url"),
                    audio.getInteger("duration_sec"), audio.getString("format"));
            passageInfo = new StartLearningPathDayResponse.Passage(
                    passage.getString("title"), passage.getString("text"), audioInfo);
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
