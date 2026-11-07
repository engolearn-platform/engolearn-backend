package fit.iuh.engolearn.learningpath.mapper;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentSkillDto;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class LearningPathAssessmentMapperTest {
    @ParameterizedTest
    @CsvSource({"R01,Reading for Specific Information,READING", "R02,Reading for Gist,READING",
            "R04,Inference & Author's View,READING", "L01,Listening for Gist,LISTENING",
            "L03,Listening for Specific Information,LISTENING"})
    void mapsPublishedAssessmentCodesWithoutDependingOnVietnameseNames(String code, String name, String section) {
        var result = new AssessmentResultDto("native-id", Instant.parse("2026-11-01T00:00:00Z"), "B1", "paper-v2",
                List.of(new AssessmentSkillDto(code, 2, 10, 20, AssessmentSkillBand.NEEDS_PRACTICE)));
        var snapshot = LearningPathAssessmentMapper.from(result, "pfc-demo-learner-v2");
        assertThat(snapshot.attemptId()).isEqualTo("native-id");
        assertThat(snapshot.userId()).isEqualTo("pfc-demo-learner-v2");
        assertThat(snapshot.paperId()).isEqualTo("paper-v2");
        assertThat(snapshot.results().getFirst().microSkill()).isEqualTo(name);
        assertThat(snapshot.results().getFirst().skill()).isEqualTo(section);
        assertThat(snapshot.results().getFirst().code()).isEqualTo(code);
        assertThat(snapshot.results().getFirst().percent()).isEqualTo(20);
    }

    @Test
    void doesNotGuessTheMeaningOfAnUnknownCode() {
        var result = new AssessmentResultDto("native-id", Instant.now(), "B1", "paper",
                List.of(new AssessmentSkillDto("UNKNOWN", 2, 10, 20, AssessmentSkillBand.NEEDS_PRACTICE)));
        assertThatThrownBy(() -> LearningPathAssessmentMapper.from(result, "learner")).isInstanceOf(IllegalArgumentException.class);
    }
}
