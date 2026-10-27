package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.SaveLearningPathAnswerDraftRequest;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.LearningPathDraft;
import fit.iuh.engolearn.learningpath.model.LearningPathInactivity;
import fit.iuh.engolearn.learningpath.model.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPathAnswerDraftServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    private static final Date MIDNIGHT = Date.from(Instant.parse("2026-10-09T17:00:00Z"));
    private final ObjectId pathId = new ObjectId();
    private final ObjectId userId = new ObjectId();
    private final LearningPathRepository paths = mock(LearningPathRepository.class);
    private final LearningPathPracticeRepository practice = mock(LearningPathPracticeRepository.class);
    private final LearningPathQuestionRepository questions = mock(LearningPathQuestionRepository.class);
    private final LearningPathAnswerDraftService service = new LearningPathAnswerDraftService(
            paths, practice, questions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesSelectionWithoutGradingAndRefreshesActivityAnchor() {
        LearningPath path = path();
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList()))
                .thenReturn(List.of(snapshot()));
        when(practice.saveDraft(any(), any(), eq(1), eq(1), eq("q-1"),
                any(), any(), any(), any(), any(), any())).thenReturn(true);

        var result = service.save(pathId.toHexString(), 1, 1, "q-1", userId.toHexString(),
                new SaveLearningPathAnswerDraftRequest("B"));

        assertThat(result.selectedOptionId()).isEqualTo("B");
        assertThat(result.savedAt()).isEqualTo(Date.from(NOW));
        assertThat(path.getWeeks().getFirst().getDays().getFirst().getItems().getFirst()
                .getAnswer()).isNull();
        ArgumentCaptor<LearningPathDraft> draft = ArgumentCaptor.forClass(LearningPathDraft.class);
        verify(practice).saveDraft(eq(pathId), eq(userId), eq(1), eq(1), eq("q-1"),
                eq(MIDNIGHT), eq(Date.from(NOW)), draft.capture(), eq("2026-10-09"),
                eq(Date.from(Instant.parse("2026-10-23T17:00:00Z"))), eq(MIDNIGHT));
        assertThat(draft.getValue().getSelectedOptionId()).isEqualTo("B");
    }

    @Test
    void rejectsSaveAfterDayDeadline() {
        LearningPath path = path();
        path.getWeeks().getFirst().getDays().getFirst().setAutoCloseAt(Date.from(NOW));
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));

        assertThatThrownBy(() -> service.save(pathId.toHexString(), 1, 1, "q-1",
                userId.toHexString(), new SaveLearningPathAnswerDraftRequest("B")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(practice, never()).saveDraft(any(), any(), any(Integer.class),
                any(Integer.class), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsOptionAbsentFromSnapshot() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path()));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList()))
                .thenReturn(List.of(snapshot()));

        assertThatThrownBy(() -> service.save(pathId.toHexString(), 1, 1, "q-1",
                userId.toHexString(), new SaveLearningPathAnswerDraftRequest("D")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private LearningPath path() {
        LearningPath path = new LearningPath();
        path.setId(pathId);
        path.setUserId(userId);
        path.setActive(true);
        path.setStatus("IN_PROGRESS");
        path.setNextRunAt(MIDNIGHT);
        LearningPathInactivity inactivity = new LearningPathInactivity();
        inactivity.setCancelAt(Date.from(Instant.parse("2026-10-23T17:00:00Z")));
        path.setInactivity(inactivity);
        LearningPathDayItem item = new LearningPathDayItem();
        item.setQuestionId("q-1");
        LearningPathDay day = new LearningPathDay();
        day.setDayNo(1);
        day.setStatus("IN_PROGRESS");
        day.setAutoCloseAt(MIDNIGHT);
        day.setItems(List.of(item));
        LearningPathWeek week = new LearningPathWeek();
        week.setWeekNo(1);
        week.setStatus("OPEN");
        week.setCloseAt(Date.from(Instant.parse("2026-10-15T17:00:00Z")));
        week.setDays(List.of(day));
        path.setWeeks(List.of(week));
        return path;
    }

    private Document snapshot() {
        return new Document("question_id", "q-1")
                .append("options", List.of(new Document("option_id", "A"),
                        new Document("option_id", "B")));
    }
}
