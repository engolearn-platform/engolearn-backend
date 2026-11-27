package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults;
import fit.iuh.engolearn.assessment.contract.dto.*;
import fit.iuh.engolearn.assessment.contract.exception.AssessmentHistoryInvalidQueryException;
import fit.iuh.engolearn.companion.service.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import static fit.iuh.engolearn.companion.service.impl.BusinessToolResponse.*;

@Component
public class CompanionAssessmentHistoryToolHandler implements CompanionToolHandler {
    private final AssessmentCompanionResults assessments;
    private final BusinessToolResponse response;

    public CompanionAssessmentHistoryToolHandler(AssessmentCompanionResults assessments, ObjectMapper mapper) {
        this.assessments = assessments;
        this.response = new BusinessToolResponse(mapper);
    }

    @Override public List<CompanionToolDefinition> definitions() {
        return List.of(new CompanionToolDefinition("getAssessmentHistory",
                "Get dated account-visible assessment history. SKILLS includes visible skill rows and like-for-like observations only when same pinned non-demo paper and visible equal denominator. Never infer validated improvement or rank hidden skills.",
                """
                {"type":"object","properties":{"limit":{"type":"integer","minimum":2,"maximum":5},"cursor":{"type":"string","maxLength":512},"detail":{"type":"string","enum":["SUMMARY","SKILLS"]}},"additionalProperties":false}
                """));
    }

    @Override public String execute(String name, Map<String,Object> input, CompanionToolExecutionContext context) {
        if (!"getAssessmentHistory".equals(name)) return response.error("UNSUPPORTED");
        String owner = context.learnerId();
        if (owner == null || owner.isBlank()) return response.forbidden();
        try {
            var a = args(input,"limit","cursor","detail");
            int limit = limit(a,2,2);
            boolean skills = "SKILLS".equals(choice(a,"detail","SUMMARY","SUMMARY","SKILLS"));
            var page = assessments.historyForCompanion(owner,
                    new AssessmentCompanionHistoryQueryDto(limit,text(a,"cursor",512,null)));
            if (page.coverage() == null || page.items().size() > limit) return response.error("INCONSISTENT_STATE");
            var rows = new ArrayList<Map<String,Object>>();
            for (var row : page.items()) {
                var r = row.result();
                if (r == null || r.attemptId() == null || r.attemptId().isBlank() || r.submittedAt() == null
                        || r.scope() == null || r.skills().size() > ("PREVIEW".equals(r.scope().name()) ? 2 : 5)
                        || r.skills().stream().anyMatch(s -> !validSkill(s)))
                    return response.error("INCONSISTENT_STATE");
                var output = fields("attemptId",r.attemptId(),"submittedAt",r.submittedAt(),
                        "officialScore",r.officialScore(),"level",r.level(),"scope",r.scope(),
                        "demonstration",row.demonstration());
                if (skills) output.put("skills",r.skills().stream().map(s -> fields("code",s.code(),
                        "name",bounded(s.name(),160),"section",s.section(),"correct",s.correct(),
                        "total",s.total(),"percentage",s.percentage(),"band",s.band())).toList());
                rows.add(output);
            }
            var observations = new ArrayList<Map<String,Object>>();
            if (skills) {
                for (int i = 0; i < page.items().size(); i++) {
                    for (int j = i + 1; j < page.items().size(); j++) {
                        var newer = page.items().get(i);
                        var older = page.items().get(j);
                        if (!comparable(newer,older)) continue;
                        for (var n : newer.result().skills()) {
                            for (var o : older.result().skills()) {
                                if (matching(n,o)) observations.add(fields("newerAttemptId",newer.result().attemptId(),
                                        "olderAttemptId",older.result().attemptId(),"code",n.code(),
                                        "section",n.section(),"denominator",n.total(),
                                        "newerPercentage",n.percentage(),"olderPercentage",o.percentage()));
                            }
                        }
                    }
                }
            }
            return response.page(rows,page.hasMore(),page.nextCursor(),page.coverage().name(),page.asOf(),limit,
                    Map.of("comparison",observations.isEmpty() ? "UNKNOWN" : "DESCRIPTIVE_ONLY",
                            "observations",observations));
        } catch (InvalidArguments | AssessmentHistoryInvalidQueryException invalid) {
            return response.invalid();
        } catch (RuntimeException unavailable) {
            return response.error("DEPENDENCY_UNAVAILABLE");
        }
    }

    private boolean comparable(AssessmentCompanionHistoryRowDto a, AssessmentCompanionHistoryRowDto b) {
        return !a.demonstration() && !b.demonstration() && a.paperId() != null && !a.paperId().isBlank()
                && a.paperId().equals(b.paperId()) && a.paperVersion() == b.paperVersion()
                && a.rulesVersion() == b.rulesVersion() && a.paperVersion() > 0 && a.rulesVersion() > 0
                && !a.result().attemptId().equals(b.result().attemptId())
                && a.result().submittedAt().isAfter(b.result().submittedAt());
    }

    private static boolean validSkill(AssessmentCompanionSkillDto skill) {
        return skill.total() >= 0 && skill.correct() >= 0 && skill.correct() <= skill.total()
                && skill.percentage() >= 0 && skill.percentage() <= 100
                && (skill.total() == 0 ? skill.percentage() == 0
                    : Math.abs(skill.percentage() - 100.0 * skill.correct() / skill.total()) < 1);
    }

    private boolean matching(AssessmentCompanionSkillDto a, AssessmentCompanionSkillDto b) {
        return a.code() != null && !a.code().isBlank() && a.code().equals(b.code())
                && a.section() != null && Objects.equals(a.section(),b.section())
                && a.total() > 0 && a.total() == b.total()
                && a.correct() >= 0 && a.correct() <= a.total() && b.correct() >= 0 && b.correct() <= b.total()
                && a.percentage() >= 0 && a.percentage() <= 100 && b.percentage() >= 0 && b.percentage() <= 100;
    }
}
