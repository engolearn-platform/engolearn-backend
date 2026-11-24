package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionImportRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest.OptionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionStatusRequest;
import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionOptionVo;
import fit.iuh.engolearn.placement.repository.PlacementQuestionRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAdminQuestionServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlacementAdminQuestionServiceTest {
    private static final List<OptionRequest> OPTIONS = List.of(new OptionRequest("1", "A", "Alpha"),
            new OptionRequest("2", "B", "Beta"), new OptionRequest("3", "C", "Gamma"),
            new OptionRequest("4", "D", "Delta"));
    private final PlacementQuestionRepository questions = mock(PlacementQuestionRepository.class);
    private final PlacementAdminQuestionMedia media = mock(PlacementAdminQuestionMedia.class);
    private final PlacementTransactionExecutor transactions = mock(PlacementTransactionExecutor.class);
    private final PlacementAdminQuestionServiceImpl service = new PlacementAdminQuestionServiceImpl(questions, media,
            transactions);

    @Test
    void savesNormalizedCodeAsDraftWithRevisionZero() {
        when(questions.insert(any(PlacementQuestion.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.create(request(" pt-1  ", OPTIONS, null));

        assertThat(result.code()).isEqualTo("PT-1");
        assertThat(result.status()).isEqualTo(PlacementQuestionStatus.DRAFT);
        assertThat(result.revision()).isZero();
    }

    @Test
    void reportsInvalidOptionLabelsBeforeWriting() {
        var invalid = List.of(OPTIONS.get(0), new OptionRequest("2", "a", "Beta"), OPTIONS.get(2), OPTIONS.get(3));

        assertThatThrownBy(() -> service.create(request("PT-1", invalid, null)))
                .isInstanceOfSatisfying(PlacementQuestionValidationException.class,
                        error -> assertThat(error.fields()).containsKey("options"));
        verifyNoInteractions(questions);
    }

    @Test
    void requiresPassageOnReadingActivation() {
        when(questions.findById("q-1")).thenReturn(Optional.of(existing(0)));

        assertThatThrownBy(() -> service.changeStatus("q-1",
                new PlacementAdminQuestionStatusRequest(PlacementQuestionStatus.ACTIVE, 0L)))
                .isInstanceOfSatisfying(PlacementQuestionValidationException.class,
                        error -> assertThat(error.fields()).containsKey("passage"));
    }

    @Test
    void reportsConflictWhenConcurrentWriteWinsTheRevisionCheck() {
        when(questions.findById("q-1")).thenReturn(Optional.of(existing(2)));
        when(questions.updateIfCurrent(any(PlacementQuestion.class), eq(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("q-1", request("PT-1", OPTIONS, 2L)))
                .isInstanceOfSatisfying(PlacementRequestException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsDuplicateNormalizedImportCodesBeforeStartingTransaction() {
        var payload = new PlacementAdminQuestionImportRequest(
                List.of(request("pt-1", OPTIONS, null), request(" PT-1 ", OPTIONS, null)));

        assertThatThrownBy(() -> service.importQuestions(payload))
                .isInstanceOfSatisfying(PlacementQuestionValidationException.class,
                        error -> assertThat(error.fields()).containsKey("questions[1].code"));
        verifyNoInteractions(questions, transactions);
    }

    private PlacementAdminQuestionRequest request(String code, List<OptionRequest> options, Long revision) {
        return new PlacementAdminQuestionRequest(code, PlacementSkill.READING, PlacementLevel.B1, "Prompt", "",
                options, "1", "", "", "", revision);
    }

    private PlacementQuestion existing(long revision) {
        return PlacementQuestion.builder().id("q-1").code("PT-1").revision(revision).skill(PlacementSkill.READING)
                .level(PlacementLevel.B1).status(PlacementQuestionStatus.DRAFT).prompt("Prompt").passage("")
                .options(OPTIONS.stream().map(option ->
                        new PlacementQuestionOptionVo(option.id(), option.label(), option.text())).toList())
                .correctOptionId("1").build();
    }
}
