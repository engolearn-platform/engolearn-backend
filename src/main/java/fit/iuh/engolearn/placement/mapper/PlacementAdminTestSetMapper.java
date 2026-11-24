package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse.QuestionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementOptionResponse;
import fit.iuh.engolearn.placement.model.PlacementLegacySeed;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.util.List;

public final class PlacementAdminTestSetMapper {
    private PlacementAdminTestSetMapper() {}

    public static PlacementAdminTestSetResponse toResponse(PlacementTestSet set, List<QuestionResponse> snapshot) {
        return new PlacementAdminTestSetResponse(set.getId(), set.getCode(), set.getName(), set.getVersion(),
                set.getStatus(), set.orderedQuestionIds(), snapshot, set.getCreatedAt(), set.getRevision(),
                set.getStatus() == PlacementTestSetStatus.DRAFT && PlacementLegacySeed.matches(set));
    }

    public static QuestionResponse liveQuestion(PlacementQuestion question, String audioUrl) {
        var options = question.getOptions() == null ? List.<PlacementOptionResponse>of()
                : question.getOptions().stream()
                .map(option -> new PlacementOptionResponse(option.getId(), option.getLabel(), option.getText())).toList();
        return new QuestionResponse(question.getId(), code(question.getCode(), question.getId()),
                text(question.getPrompt()), question.getSkill(), question.getLevel(), options,
                text(question.getCorrectOptionId()), text(question.getPassage()), text(audioUrl),
                text(question.getTranscript()), text(question.getExplanation()), question.getStatus());
    }

    public static QuestionResponse snapshotQuestion(PlacementSnapshotItemVo item, PlacementQuestionStatus status) {
        var options = item.getOptions() == null ? List.<PlacementOptionResponse>of()
                : item.getOptions().stream()
                .map(option -> new PlacementOptionResponse(option.getId(), option.getLabel(), option.getText())).toList();
        return new QuestionResponse(item.getId(), code(item.getCode(), item.getId()), text(item.getPrompt()),
                item.getSkill(), item.getLevel(), options, text(item.getCorrectOptionId()), text(item.getPassage()),
                item.getAudio() == null ? "" : text(item.getAudio().getUrl()),
                text(item.getTranscript()), text(item.getExplanation()), status);
    }

    private static String code(String code, String id) {
        return code == null || code.isBlank() ? id : code;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
