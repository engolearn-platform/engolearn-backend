package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionSummaryDto;
import fit.iuh.engolearn.companion.service.CompanionToolDefinition;
import fit.iuh.engolearn.companion.service.CompanionToolHandler;
import fit.iuh.engolearn.companion.service.CompanionToolExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@org.springframework.core.annotation.Order(0)
public class CompanionAssessmentToolHandler implements CompanionToolHandler {
    private static final String LATEST_ASSESSMENT = "getLatestAssessmentResult";
    private static final CompanionToolDefinition ASSESSMENT_TOOL = new CompanionToolDefinition(
            LATEST_ASSESSMENT,
            "Get the learner's latest account-visible assessment summary or visible skill details. Use detail=SKILLS for skill guidance. Only filter with skillCodes when you already know the exact assessment skill codes; for a named skill such as listening, omit skillCodes so the tool can return the account-visible rows. If a filtered request returns NO_DATA, retry detail=SKILLS without skillCodes. Request detail only when it improves the advice.",
            "{\"type\":\"object\",\"properties\":{\"detail\":{\"type\":\"string\",\"enum\":[\"SUMMARY\",\"SKILLS\"],\"description\":\"Use SKILLS to retrieve visible skill rows for skill-specific advice.\"},\"skillCodes\":{\"type\":\"array\",\"description\":\"Optional exact assessment skill codes only when already known. Omit for a named skill or when unsure; never infer a code from a skill name.\",\"items\":{\"type\":\"string\"},\"maxItems\":5}},\"additionalProperties\":false}");

    private final AssessmentCompanionResults assessments;
    private final ObjectMapper mapper;
    public CompanionAssessmentToolHandler(AssessmentCompanionResults assessments, ObjectMapper mapper) {
        this.assessments = Objects.requireNonNull(assessments, "assessments");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override public List<CompanionToolDefinition> definitions() { return List.of(ASSESSMENT_TOOL); }

    @Override public String execute(String name, Map<String, Object> arguments, CompanionToolExecutionContext context) {
        String learnerId = context.learnerId();
        Runnable onAssessmentLookup = context.onAssessmentLookup();
        if (!LATEST_ASSESSMENT.equals(name)) return result("ERROR", "The requested tool is unavailable.");
        if (learnerId == null || learnerId.isBlank()) return result("FORBIDDEN", "Assessment context is unavailable.");
        ToolArguments parsed = parseArguments(arguments);
        if (parsed == null) return result("INVALID_ARGUMENTS", "Tool arguments are invalid.");
        onAssessmentLookup.run();
        try {
            AssessmentCompanionSummaryDto summary = assessments.latestForCompanion(learnerId).orElse(null);
            if (summary == null) return json(Map.of("status", "NO_DATA"));
            if (parsed.detail().equals("SUMMARY")) {
                return json(new AssessmentSummaryResult("SUCCESS", summary.attemptId(), summary.submittedAt(),
                        summary.officialScore(), summary.level(), summary.scope().name()));
            }
            List<?> visibleSkills = parsed.skillCodes().isEmpty() ? summary.skills()
                    : summary.skills().stream().filter(skill -> parsed.skillCodes().contains(skill.code())).toList();
            if (visibleSkills.isEmpty()) {
                return result("NO_DATA", parsed.skillCodes().isEmpty()
                        ? "No account-visible skill details are available."
                        : "No matching account-visible skill details were found. Retry detail=SKILLS without skillCodes.");
            }
            return json(new AssessmentSkillsResult("SUCCESS", summary.attemptId(), summary.submittedAt(),
                    summary.officialScore(), summary.level(), summary.scope().name(), visibleSkills));
        } catch (RuntimeException failure) {
            return result("ERROR", "Assessment context is temporarily unavailable.");
        }
    }

    private ToolArguments parseArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return new ToolArguments("SUMMARY", List.of());
        if (arguments.keySet().stream().anyMatch(key -> !key.equals("detail") && !key.equals("skillCodes"))) return null;
        Object rawDetail = arguments.getOrDefault("detail", "SUMMARY");
        if (!(rawDetail instanceof String detail) || !(detail.equals("SUMMARY") || detail.equals("SKILLS"))) return null;
        Object rawSkills = arguments.get("skillCodes");
        if (detail.equals("SUMMARY") && rawSkills != null) return null;
        if (rawSkills == null) return new ToolArguments(detail, List.of());
        if (!(rawSkills instanceof List<?> values) || values.size() > 5
                || values.stream().anyMatch(item -> !(item instanceof String code) || code.isBlank() || code.length() > 80)) {
            return null;
        }
        return new ToolArguments(detail, values.stream().map(String.class::cast).distinct().toList());
    }

    private String result(String status, String message) {
        return json(Map.of("status", status, "message", message));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not serialize companion tool result", failure);
        }
    }

    private record ToolArguments(String detail, List<String> skillCodes) {}
    private record AssessmentSummaryResult(String status, String attemptId, java.time.Instant submittedAt,
            Integer officialScore, String level, String scope) {}
    private record AssessmentSkillsResult(String status, String attemptId, java.time.Instant submittedAt,
            Integer officialScore, String level, String scope, List<?> skills) {}
}
