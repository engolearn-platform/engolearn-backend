package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayResult;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathProgress;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPathDayResultServiceTest {
    private final ObjectId pathId = new ObjectId();
    private final ObjectId userId = new ObjectId();
    private final LearningPathRepository paths = mock(LearningPathRepository.class);
    private final LearningPathQuestionRepository questions = mock(LearningPathQuestionRepository.class);
    private final LearningPathDayResultService service = new LearningPathDayResultService(paths, questions);

    @Test
    void closedDayWithoutAnswersIncludesAllExplanationsAndNoScore() {
        LearningPath path = path(LearningPathDayStatus.CLOSED);
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList())).thenReturn(snapshots());
        var result = service.getResult(pathId.toHexString(), 1, 1, userId.toHexString());
        assertThat(result.answered()).isZero();
        assertThat(result.practiceScore()).isNull();
        assertThat(result.questions()).hasSize(10).allSatisfy(question -> {
            assertThat(question.answerStatus()).isEqualTo("UNANSWERED");
            assertThat(question.selectedOptionId()).isNull();
            assertThat(question.correctOptionId()).isEqualTo("A");
            assertThat(question.explanation()).isEqualTo("Explanation");
        });
        assertThat(result.questions().getFirst().passage().transcript()).isEqualTo("Transcript");
    }

    @Test
    void legacySubmittedDayIsReturnedAsSubmitted() {
        LearningPath path = path(LearningPathDayStatus.CLOSED);
        path.getWeeks().getFirst().getDays().getFirst().setCloseReason(LearningPathDayCloseReason.SUBMITTED);
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList())).thenReturn(snapshots());
        assertThat(service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()).status())
                .isEqualTo("SUBMITTED");
    }

    @Test
    void submittedDayCanBeReviewed() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path(LearningPathDayStatus.SUBMITTED)));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList())).thenReturn(snapshots());
        assertThat(service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()).status())
                .isEqualTo("SUBMITTED");
    }

    @Test
    void openDayDoesNotExposeSnapshots() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path(LearningPathDayStatus.IN_PROGRESS)));
        assertThatThrownBy(() -> service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(questions, never()).findSnapshots(eq(pathId), eq(userId), anyList());
    }

    @Test
    void cancelledPathCannotBeReviewed() {
        LearningPath path = path(LearningPathDayStatus.CLOSED);
        path.setStatus(LearningPathStatus.CANCELLED);
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path));
        assertThatThrownBy(() -> service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(questions, never()).findSnapshots(eq(pathId), eq(userId), anyList());
    }

    @Test
    void missingPathOrWrongOwnerReturnsNotFound() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void missingSnapshotFailsInsteadOfReturningPartialReview() {
        when(paths.findByIdAndUserId(pathId, userId)).thenReturn(Optional.of(path(LearningPathDayStatus.CLOSED)));
        when(questions.findSnapshots(eq(pathId), eq(userId), anyList())).thenReturn(snapshots().subList(0, 9));
        assertThatThrownBy(() -> service.getResult(pathId.toHexString(), 1, 1, userId.toHexString()))
                .isInstanceOf(IllegalStateException.class);
    }

    private LearningPath path(LearningPathDayStatus status) {
        var items = IntStream.rangeClosed(1, 10).mapToObj(i -> {
            var item = new LearningPathDayItem();
            item.setOrder(i);
            item.setQuestionId("q-" + i);
            item.setMicroSkill("Reading for Gist");
            return item;
        }).toList();
        var progress = new LearningPathProgress();
        progress.setAssigned(10);
        progress.setAnswered(0);
        progress.setCorrect(0);
        var result = new LearningPathDayResult();
        result.setWrong(0);
        result.setCompletionRate(0.0);
        var day = new LearningPathDay();
        day.setDayNo(1);
        day.setStatus(status);
        day.setItems(items);
        day.setProgress(progress);
        day.setResult(result);
        var week = new LearningPathWeek();
        week.setWeekNo(1);
        week.setDays(List.of(day));
        var path = new LearningPath();
        path.setStatus(LearningPathStatus.IN_PROGRESS);
        path.setWeeks(List.of(week));
        return path;
    }

    private List<Document> snapshots() {
        return IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("question_id", "q-" + i)
                .append("micro_skill", "Reading for Gist").append("skill", "READING")
                .append("prompt", "Question " + i).append("correct_option_id", "A")
                .append("explanation", "Explanation")
                .append("options", List.of(new Document("option_id", "A"), new Document("option_id", "B"),
                        new Document("option_id", "C"), new Document("option_id", "D")))
                .append("passage", new Document("title", "Passage").append("transcript", "Transcript")))
                .toList();
    }
}
