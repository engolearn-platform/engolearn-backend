package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentResults;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentSkillDto;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse.State;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningPathRecommendationServiceImplTest {
    private final AssessmentResults results = mock(AssessmentResults.class);
    private final LearningPathRepository paths = mock(LearningPathRepository.class);
    private final QuestionPoolRepository pool = mock(QuestionPoolRepository.class);
    private final LearningPathRecommendationServiceImpl service = new LearningPathRecommendationServiceImpl(results, paths, pool);

    @Test
    void usesTheExactNativeAttemptAndDoesNotSampleOrWriteDuringRecommendation() {
        when(results.findCompleted("learner", "native-old")).thenReturn(Optional.of(result(20)));
        when(pool.countActive("Reading for Gist", "B1")).thenReturn(30L);
        var recommendation = service.recommendForAssessment("learner", "native-old");
        assertThat(recommendation.state()).isEqualTo(State.AVAILABLE);
        assertThat(recommendation.microSkills()).hasSize(1);
        assertThat(recommendation.microSkills().getFirst().percent()).isEqualTo(20);
        verify(results, never()).latest(any());
        verify(pool, never()).sampleActive(any(), any(), anyInt());
        verify(paths, never()).save(any());
    }

    @Test
    void fiftyPercentIsNotWeakAndDoesNotRequirePoolQuestions() {
        when(results.findCompleted("learner", "native-old")).thenReturn(Optional.of(result(50)));
        assertThat(service.recommend("learner", "native-old").state()).isEqualTo(State.NO_WEAK_SKILLS);
        verifyNoInteractions(pool);
    }

    @Test
    void excludesOccupiedSkillsAndSkillsWithOnlyTwentyNineQuestions() {
        when(results.findCompleted("learner", "native-old")).thenReturn(Optional.of(result(20)));
        when(pool.countActive("Reading for Gist", "B1")).thenReturn(29L);
        assertThat(service.recommend("learner", "native-old").state()).isEqualTo(State.NO_ELIGIBLE_SKILLS);
        var active = new LearningPath();
        var skill = new LearningPathMicroSkillSnapshot();
        skill.setMicroSkill("Reading for Gist");
        active.setMicroSkills(List.of(skill));
        when(paths.findByUserIdAndActiveTrue("learner")).thenReturn(List.of(active));
        when(pool.countActive("Reading for Gist", "B1")).thenReturn(30L);
        assertThat(service.recommend("learner", "native-old").state()).isEqualTo(State.NO_ELIGIBLE_SKILLS);
    }

    @Test
    void absentOrForeignAssessmentNeverExposesAPath() {
        assertThatThrownBy(() -> service.recommend("learner", "foreign"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(paths, pool);
    }

    @Test
    void anExistingSourceIsIdempotentOnlyForTheSameOwner() {
        when(results.findCompleted("learner", "native-old")).thenReturn(Optional.of(result(20)));
        var existing = new LearningPath();
        existing.setId("existing-path");
        existing.setUserId("learner");
        when(paths.findBySourceAssessmentAttemptId("native-old")).thenReturn(Optional.of(existing));
        assertThat(service.recommend("learner", "native-old").state()).isEqualTo(State.ALREADY_CREATED);
        existing.setUserId("another");
        assertThatThrownBy(() -> service.recommend("learner", "native-old"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "unknown", "zero-total", "bad-percentage", "missing-band"})
    void rejectsIncompleteOrUnsupportedSkills(String problem) {
        List<AssessmentSkillDto> skills = new ArrayList<>(result(20).skills());
        switch (problem) {
            case "missing" -> skills.removeFirst();
            case "duplicate" -> skills.set(1, skills.getFirst());
            case "unknown" -> skills.set(0, new AssessmentSkillDto("UNKNOWN", 2, 10, 20, AssessmentSkillBand.NEEDS_PRACTICE));
            case "zero-total" -> skills.set(0, new AssessmentSkillDto("R02", 0, 0, 0, AssessmentSkillBand.NEEDS_PRACTICE));
            case "bad-percentage" -> skills.set(0, new AssessmentSkillDto("R02", 2, 10, 90, AssessmentSkillBand.MEETS_THRESHOLD));
            case "missing-band" -> skills.set(0, new AssessmentSkillDto("R02", 2, 10, 20, null));
        }
        when(results.findCompleted("learner", "native-old")).thenReturn(Optional.of(new AssessmentResultDto("native-old", Instant.now(), "B1", "paper", skills)));
        assertThatThrownBy(() -> service.recommend("learner", "native-old"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(pool);
    }

    private AssessmentResultDto result(int percentage) {
        return new AssessmentResultDto("native-old", Instant.now(), "B1", "paper",
                List.of("R02", "R01", "R04", "L01", "L03").stream().map(code -> {
                    int percent = code.equals("R02") ? percentage : 80;
                    return new AssessmentSkillDto(code, percent / 10, 10, percent,
                            percent < 50 ? AssessmentSkillBand.NEEDS_PRACTICE : AssessmentSkillBand.MEETS_THRESHOLD);
                }).toList());
    }
}
