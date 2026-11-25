package fit.iuh.engolearn.companion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionSkillDto;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionSummaryDto;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionScope;
import fit.iuh.engolearn.companion.service.impl.CompanionToolRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CompanionToolRegistryTest {
    private final AssessmentCompanionResults assessments = mock(AssessmentCompanionResults.class);
    private final CompanionToolRegistry registry = new CompanionToolRegistry(assessments, JsonMapper.builder().build());
    private final Instant submitted = Instant.parse("2026-11-25T10:00:00Z");

    @BeforeEach void setUp() {
        when(assessments.latestForCompanion("learner-1")).thenReturn(Optional.of(
                new AssessmentCompanionSummaryDto("attempt-1", submitted, 68, "B1", AssessmentCompanionScope.PREVIEW,
                        List.of(new AssessmentCompanionSkillDto("READING", "Reading", "Example", "READING", 8, 10, 80, null),
                                new AssessmentCompanionSkillDto("LISTENING", "Listening", "Example", "LISTENING", 6, 10, 60, null)))));
    }

    @Test void summaryOmitsSkillRowsAndSkillDetailCanBeNarrowedToVisibleCodes() {
        String summary = registry.execute("getLatestAssessmentResult", Map.of(), "learner-1");
        assertTrue(summary.contains("\"status\":\"SUCCESS\""));
        assertTrue(summary.contains("\"scope\":\"PREVIEW\""));
        assertFalse(summary.contains("READING"));

        String detail = registry.execute("getLatestAssessmentResult",
                Map.of("detail", "SKILLS", "skillCodes", List.of("READING")), "learner-1");
        assertTrue(detail.contains("READING"));
        assertFalse(detail.contains("LISTENING"));
    }

    @Test void unknownSkillCodeReturnsSafeNoDataAndSuggestsAnUnfilteredVisibleLookup() {
        String result = registry.execute("getLatestAssessmentResult",
                Map.of("detail", "SKILLS", "skillCodes", List.of("L03")), "learner-1");

        assertTrue(result.contains("\"status\":\"NO_DATA\""));
        assertTrue(result.contains("Retry detail=SKILLS without skillCodes"));
        assertFalse(result.contains("READING"));
        assertFalse(result.contains("LISTENING"));
    }

    @Test void argumentsNeverSelectAnOwnerAndAssessmentFailuresReturnSafeTypedError() {
        String invalid = registry.execute("getLatestAssessmentResult", Map.of("userId", "other"), "learner-1");
        assertTrue(invalid.contains("INVALID_ARGUMENTS"));
        verify(assessments, never()).latestForCompanion("other");

        when(assessments.latestForCompanion("learner-1")).thenThrow(new IllegalStateException("sensitive internal error"));
        String failure = registry.execute("getLatestAssessmentResult", Map.of("detail", "SKILLS"), "learner-1");
        assertTrue(failure.contains("\"status\":\"ERROR\""));
        assertFalse(failure.contains("sensitive internal error"));
    }

    @Test void toolCanBeDisabledAndNoDataIsTyped() {
        assertTrue(registry.definitions(false).isEmpty());
        when(assessments.latestForCompanion("learner-2")).thenReturn(Optional.empty());
        assertTrue(registry.execute("getLatestAssessmentResult", Map.of(), "learner-2").contains("NO_DATA"));
    }
}
