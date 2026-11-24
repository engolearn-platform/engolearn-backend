package fit.iuh.engolearn.placement.mapper;

import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementOptionVo;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementResultMapperTest {
    private static final String TRANSCRIPT = "This train goes to Greenford. Please have your tickets ready.";

    @Test
    void keepsAReasonOrSuppressesLegacyFillerWithoutReturningTheFullTranscript() {
        var snapshot = question("Đáp án B: Greenford. Đối chiếu thông tin được nêu trong ngữ liệu riêng của câu này."
                + " Ngữ liệu: “" + TRANSCRIPT + "”");
        assertThat(PlacementResultMapper.learnerExplanation(snapshot)).isNull();
        assertThat(snapshot.getExplanation()).contains(TRANSCRIPT);

        String reason = "Cụm “goes to Greenford” nêu điểm đến.";
        snapshot.setExplanation("Đáp án B: Greenford. " + reason + " Ngữ liệu: “" + TRANSCRIPT + "”");
        assertThat(PlacementResultMapper.learnerExplanation(snapshot)).isEqualTo(reason);
    }

    @Test
    void leavesTheNewExplanationIntactAndAcceptsMissingText() {
        String text = "Nghe cụm “goes to Greenford”. Từ “to” dẫn vào ga đến.";
        assertThat(PlacementResultMapper.learnerExplanation(question(" " + text + " "))).isEqualTo(text);
        assertThat(PlacementResultMapper.learnerExplanation(question(null))).isNull();
    }

    private PlacementSnapshotItemVo question(String explanation) {
        return PlacementSnapshotItemVo.builder().skill(PlacementSkill.LISTENING).correctOptionId("b")
                .options(List.of(new PlacementOptionVo("b", "B", "Greenford")))
                .transcript(TRANSCRIPT).explanation(explanation).build();
    }
}
