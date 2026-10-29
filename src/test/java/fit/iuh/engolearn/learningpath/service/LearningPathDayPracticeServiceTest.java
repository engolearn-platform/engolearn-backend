package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathConfig;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDraft;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathInactivity;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPathDayPracticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    private static final Instant NEXT_MIDNIGHT = Instant.parse("2026-10-09T17:00:00Z");

    private final LearningPathRepository paths = mock(LearningPathRepository.class);
    private final LearningPathPracticeRepository practice = mock(LearningPathPracticeRepository.class);
    private final LearningPathQuestionRepository questions = mock(LearningPathQuestionRepository.class);
    private final LearningPathDayPracticeService service = new LearningPathDayPracticeService(
            paths, practice, questions, Clock.fixed(NOW, ZoneOffset.UTC));
    private final ObjectId pathId = new ObjectId();
    private final ObjectId userId = new ObjectId();

    @Test
    void startsDayAndReturnsOrderedQuestionsWithoutAnswerKey() throws Exception {
        LearningPath path = path("NOT_STARTED");
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        when(questions.findSnapshots(any(), any(), anyList())).thenReturn(snapshots());
        when(practice.startDay(any(), any(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        var response = service.start(pathId.toHexString(), 1, 1, userId.toHexString());

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.startedAt()).isEqualTo(Date.from(NOW));
        assertThat(response.autoCloseAt()).isEqualTo(Date.from(NEXT_MIDNIGHT));
        assertThat(response.questions()).hasSize(10);
        assertThat(response.questions().getFirst().questionId()).isEqualTo("q-1");
        assertThat(response.questions().getLast().questionId()).isEqualTo("q-10");
        String json = JsonMapper.builder().build().writeValueAsString(response);
        assertThat(json).contains("audio", "optionId")
                .doesNotContain("correctOptionId", "correct_option_id", "explanation", "transcript");
        verify(practice).startDay(pathId, userId, 1, 1, "NOT_STARTED",
                path.getRevision(), path.getNextRunAt(), Date.from(NOW), "2026-10-09",
                Date.from(NEXT_MIDNIGHT), Date.from(NEXT_MIDNIGHT));
    }

    @Test
    void reopeningStartedDayKeepsOriginalDeadline() {
        LearningPath path = path("IN_PROGRESS");
        LearningPathDay day = path.getWeeks().getFirst().getDays().getFirst();
        day.setStatus(LearningPathDayStatus.IN_PROGRESS);
        day.setStartedAt(Date.from(NOW.minusSeconds(1800)));
        day.setAutoCloseAt(Date.from(NEXT_MIDNIGHT));
        LearningPathDraft draft = new LearningPathDraft();
        draft.setSelectedOptionId("C");
        day.getItems().getLast().setDraft(draft);
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        when(questions.findSnapshots(any(), any(), anyList())).thenReturn(snapshots());

        var response = service.start(pathId.toHexString(), 1, 1, userId.toHexString());

        assertThat(response.startedAt()).isEqualTo(day.getStartedAt());
        assertThat(response.autoCloseAt()).isEqualTo(day.getAutoCloseAt());
        assertThat(response.questions().getFirst().selectedOptionId()).isEqualTo("C");
        assertThat(response.questions().get(1).selectedOptionId()).isNull();
        verify(practice, never()).startDay(any(), any(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotRevealAnotherUsersPath() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(questions, never()).findSnapshots(any(), any(), anyList());
    }

    @Test
    void rejectsLockedWeekAndExpiredDay() {
        LearningPath path = path("IN_PROGRESS");
        LearningPathWeek week = path.getWeeks().getFirst();
        week.setStatus(LearningPathWeekStatus.LOCKED);
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));

        assertThatThrownBy(() -> service.start(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        week.setStatus(LearningPathWeekStatus.OPEN);
        week.getDays().getFirst().setStatus(LearningPathDayStatus.IN_PROGRESS);
        week.getDays().getFirst().setAutoCloseAt(Date.from(NOW));
        assertThatThrownBy(() -> service.start(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void missingSnapshotCannotStartDay() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path("NOT_STARTED")));
        when(questions.findSnapshots(any(), any(), anyList())).thenReturn(snapshots().subList(0, 9));

        assertThatThrownBy(() -> service.start(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOf(IllegalStateException.class);
        verify(practice, never()).startDay(any(), any(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void malformedSnapshotCannotStartDay() {
        List<Document> saved = snapshots();
        saved.getFirst().remove("options");
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path("NOT_STARTED")));
        when(questions.findSnapshots(any(), any(), anyList())).thenReturn(saved);

        assertThatThrownBy(() -> service.start(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOf(IllegalStateException.class);
        verify(practice, never()).startDay(any(), any(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(), any());
    }

    private LearningPath path(String status) {
        LearningPath path = new LearningPath();
        path.setId(pathId.toHexString());
        path.setUserId(userId);
        path.setStatus(LearningPathStatus.valueOf(status));
        path.setActive(true);
        path.setNextRunAt(Date.from(Instant.parse("2026-10-15T17:00:00Z")));
        LearningPathConfig config = new LearningPathConfig();
        config.setQuestionsPerDay(10);
        path.setConfig(config);
        LearningPathInactivity inactivity = new LearningPathInactivity();
        inactivity.setCancelAt(Date.from(Instant.parse("2026-10-23T17:00:00Z")));
        path.setInactivity(inactivity);
        List<LearningPathDayItem> items = new ArrayList<>();
        for (int order = 10; order >= 1; order--) {
            LearningPathDayItem item = new LearningPathDayItem();
            item.setOrder(order);
            item.setQuestionId("q-" + order);
            item.setMicroSkill("Reading for Gist");
            items.add(item);
        }
        LearningPathDay day = new LearningPathDay();
        day.setDayNo(1);
        day.setStatus(LearningPathDayStatus.NOT_STARTED);
        day.setItems(items);
        LearningPathWeek week = new LearningPathWeek();
        week.setWeekNo(1);
        week.setStatus(LearningPathWeekStatus.OPEN);
        week.setCloseAt(Date.from(Instant.parse("2026-10-15T17:00:00Z")));
        week.setDays(List.of(day));
        path.setWeeks(List.of(week));
        return path;
    }

    private List<Document> snapshots() {
        List<Document> snapshots = new ArrayList<>();
        for (int order = 1; order <= 10; order++) {
            Document audio = new Document("url", "https://example.test/audio.mp3")
                    .append("duration_sec", 30).append("format", "mp3");
            Document passage = new Document("title", "Listening")
                    .append("text", "Listen carefully").append("audio", audio)
                    .append("transcript", "secret transcript");
            List<Document> options = List.of(
                    new Document("option_id", "A").append("text", "First"),
                    new Document("option_id", "B").append("text", "Second"),
                    new Document("option_id", "C").append("text", "Third"),
                    new Document("option_id", "D").append("text", "Fourth"));
            snapshots.add(new Document("question_id", "q-" + order)
                    .append("skill", "LISTENING")
                    .append("micro_skill", "Reading for Gist")
                    .append("passage", passage)
                    .append("prompt", "Question " + order).append("options", options)
                    .append("correct_option_id", "B").append("explanation", "secret explanation"));
        }
        return snapshots;
    }
}
