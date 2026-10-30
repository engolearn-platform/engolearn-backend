package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathDayResultResponse;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathAnswer;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.util.Comparator;
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
public class LearningPathDayResultService {
    private final LearningPathRepository paths;
    private final LearningPathQuestionRepository questions;

    public LearningPathDayResultService(LearningPathRepository paths, LearningPathQuestionRepository questions) {
        this.paths = paths;
        this.questions = questions;
    }

    public LearningPathDayResultResponse getResult(String pathId, int weekNo, int dayNo, String userId) {
        ObjectId pathObjectId = new ObjectId(pathId);
        ObjectId userObjectId = new ObjectId(userId);
        var path = paths.findByIdAndUserId(pathObjectId, userObjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Learning Path"));
        if (path.getStatus() == LearningPathStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Learning Path đã bị hủy và không thể mở");
        }
        LearningPathWeek week = path.getWeeks().stream().filter(item -> item.getWeekNo() == weekNo).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tuần luyện tập"));
        LearningPathDay day = week.getDays().stream().filter(item -> item.getDayNo() == dayNo).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ngày luyện tập"));
        if (day.getStatus() != LearningPathDayStatus.COMPLETED && day.getStatus() != LearningPathDayStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ngày luyện tập chưa kết thúc");
        }
        if (day.getItems() == null || day.getItems().size() != 10 || day.getProgress() == null
                || day.getResult() == null || day.getProgress().getAssigned() == null
                || day.getProgress().getAnswered() == null || day.getProgress().getCorrect() == null
                || day.getResult().getWrong() == null) {
            throw new IllegalStateException("Learning Path day result is incomplete");
        }
        if (day.getItems().stream().anyMatch(item -> item.getOrder() == null || item.getQuestionId() == null)) {
            throw new IllegalStateException("Learning Path day questions are invalid");
        }
        List<LearningPathDayItem> items = day.getItems().stream()
                .sorted(Comparator.comparing(LearningPathDayItem::getOrder)).toList();
        List<String> ids = items.stream().map(LearningPathDayItem::getQuestionId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("Duplicate Learning Path day question");
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getOrder() != i + 1) {
                throw new IllegalStateException("Invalid Learning Path question order");
            }
        }
        Map<String, Document> snapshots = new HashMap<>();
        for (Document snapshot : questions.findSnapshots(pathObjectId, userObjectId, ids)) {
            String id = snapshot.getString("question_id");
            if (id == null || snapshots.putIfAbsent(id, snapshot) != null) {
                throw new IllegalStateException("Invalid Learning Path question snapshot");
            }
        }
        if (snapshots.size() != ids.size() || !snapshots.keySet().containsAll(ids)) {
            throw new IllegalStateException("Learning Path question snapshot is incomplete");
        }
        var review = items.stream().map(item -> toQuestion(item, snapshots.get(item.getQuestionId()))).toList();
        return new LearningPathDayResultResponse(pathId, weekNo, dayNo, day.getStatus().name(), day.getClosedAt(),
                day.getProgress().getAssigned(), day.getProgress().getAnswered(), day.getProgress().getCorrect(),
                day.getResult().getWrong(), day.getResult().getCompletionRate(), day.getResult().getPracticeScore(), review);
    }

    private LearningPathDayResultResponse.Question toQuestion(LearningPathDayItem item, Document snapshot) {
        List<Document> savedOptions = snapshot.getList("options", Document.class);
        if (snapshot.getString("prompt") == null || snapshot.getString("skill") == null
                || item.getMicroSkill() == null || !item.getMicroSkill().equals(snapshot.getString("micro_skill"))
                || snapshot.getString("correct_option_id") == null || snapshot.getString("explanation") == null
                || savedOptions == null || savedOptions.size() != 4) {
            throw new IllegalStateException("Learning Path question snapshot is invalid");
        }
        var options = savedOptions.stream().map(option -> new LearningPathDayResultResponse.Option(
                option.getString("option_id"), option.getString("text"))).toList();
        Document passage = snapshot.get("passage", Document.class);
        LearningPathDayResultResponse.Passage passageInfo = null;
        if (passage != null) {
            Document audio = passage.get("audio", Document.class);
            var audioInfo = audio == null ? null : new LearningPathDayResultResponse.Audio(
                    audio.getString("url"), audio.getInteger("duration_sec"), audio.getString("format"));
            passageInfo = new LearningPathDayResultResponse.Passage(
                    passage.getString("title"), passage.getString("text"), audioInfo, passage.getString("transcript"));
        }
        LearningPathAnswer answer = item.getAnswer();
        if (answer != null && (answer.getSelectedOptionId() == null || answer.getCorrect() == null)) {
            throw new IllegalStateException("Learning Path answer is incomplete");
        }
        String answerStatus = answer == null ? "UNANSWERED" : answer.getCorrect() ? "CORRECT" : "WRONG";
        return new LearningPathDayResultResponse.Question(item.getOrder(), item.getQuestionId(), item.getMicroSkill(),
                snapshot.getString("skill"), passageInfo, snapshot.getString("prompt"), options,
                answer == null ? null : answer.getSelectedOptionId(), snapshot.getString("correct_option_id"),
                snapshot.getString("explanation"), answerStatus);
    }
}
