package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.service.*;
import fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads;
import fit.iuh.engolearn.learningpath.contract.dto.*;
import fit.iuh.engolearn.learningpath.contract.exception.LearningPathCompanionReadException;
import fit.iuh.engolearn.learningpath.contract.enums.LearningPathAvailability;
import fit.iuh.engolearn.learningpath.contract.enums.LearningPathAction;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import static fit.iuh.engolearn.companion.service.impl.BusinessToolResponse.*;

@Component
public class CompanionLearningPathToolHandler implements CompanionToolHandler {
    private final LearningPathCompanionReads paths;
    private final BusinessToolResponse response;

    public CompanionLearningPathToolHandler(LearningPathCompanionReads paths, ObjectMapper mapper) {
        this.paths = paths;
        this.response = new BusinessToolResponse(mapper);
    }

    @Override public List<CompanionToolDefinition> definitions() {
        return List.of(new CompanionToolDefinition("getLearningPathProgress",
                "ACTIVE: current path candidates may be UNKNOWN and need app recheck. COUNTS: completed paths only; send only mode=COUNTS, no limit or cursor. RECENT: recorded starts and closures this week (Vietnam time), not comprehensive history or successful completion. For ACTIVE/RECENT omit optional fields on the first call; use a cursor only when returned in this turn.",
                """
                {"type":"object","properties":{"mode":{"type":"string","enum":["ACTIVE","COUNTS","RECENT"]},"limit":{"type":"integer","minimum":1,"maximum":5,"description":"Optional for ACTIVE or RECENT only; omit for COUNTS."},"cursor":{"type":"string","maxLength":512,"description":"Only a nextCursor returned for the same mode in this turn; otherwise omit. Never for COUNTS."}},"required":["mode"],"additionalProperties":false}
                """));
    }

    private static boolean validActiveRow(LearningPathActivePageDto.Row row) {
        return ("NOT_STARTED".equals(row.status()) || "IN_PROGRESS".equals(row.status()))
                && (row.availability() == LearningPathAvailability.UNKNOWN
                    || row.availability() == LearningPathAvailability.NOT_AVAILABLE)
                && row.nextActions().size() <= 5 && row.microSkills().size() <= 5
                && nonnegative(row.assigned()) && nonnegative(row.answered()) && nonnegative(row.correct())
                && ordered(row.correct(),row.answered()) && ordered(row.answered(),row.assigned())
                && ordered(row.correct(),row.assigned())
                && (row.currentWeekNo() == null || row.currentWeekNo() > 0)
                && (row.availability() != LearningPathAvailability.NOT_AVAILABLE
                    || row.nextActions().isEmpty())
                && row.nextActions().stream().allMatch(action -> action.weekNo() > 0 && action.dayNo() > 0
                    && (("NOT_STARTED".equals(action.dayStatus()) && action.action() == LearningPathAction.START)
                        || ("IN_PROGRESS".equals(action.dayStatus()) && action.action() == LearningPathAction.RESUME))
                    && action.availability() == LearningPathAvailability.UNKNOWN
                    && action.requiresAppRecheck());
    }

    private static boolean nonnegative(Integer value) { return value == null || value >= 0; }
    private static boolean validRate(Double value) {
        return value == null || (Double.isFinite(value) && value >= 0 && value <= 100);
    }
    private static boolean ordered(Integer lower, Integer upper) {
        return lower == null || upper == null || lower <= upper;
    }
    private static boolean validDayStatus(String value) {
        return value != null && java.util.Set.of("NOT_STARTED","IN_PROGRESS","COMPLETED","SUBMITTED","CLOSED").contains(value);
    }

    @Override public String execute(String name, Map<String, Object> input, CompanionToolExecutionContext context) {
        if (!"getLearningPathProgress".equals(name)) return response.error("UNSUPPORTED");
        String owner = context.learnerId();
        if (owner == null || owner.isBlank()) return response.forbidden();
        try {
            var a = args(input, "mode","limit","cursor");
            var mode = choice(a,"mode",null,"ACTIVE","COUNTS","RECENT");
            if ("COUNTS".equals(mode)) {
                countsOnly(a);
                var counts = paths.counts(owner);
                if (counts.asOf() == null || counts.coverage() == null
                        || !"COMPLETE".equals(counts.coverage().name()) || counts.completedPaths() < 0
                        || !"CURRENT_RECORDED_PATHS".equals(counts.scope()))
                    return response.error("INCONSISTENT_STATE");
                return response.success(counts);
            }
            var scope = context.businessContext();
            if (scope == null) return response.error("INCONSISTENT_STATE");
            int limit = limit(a,1,3);
            var cursor = text(a,"cursor",512,null);
            if (!scope.canContinue(owner,mode,cursor)) return response.invalid();
            var query = new LearningPathPageQueryDto(limit,cursor);
            if ("ACTIVE".equals(mode)) {
                var page = paths.active(owner,scope.asOf(),query);
                if (page.coverage() == null || !scope.asOf().equals(page.asOf()) || page.items().stream().anyMatch(row ->
                        !validActiveRow(row)))
                    return response.error("INCONSISTENT_STATE");
                var rows = page.items().stream().map(row -> new LearningPathActivePageDto.Row(
                        row.pathId(), row.cefrLevel(), row.status(), row.currentWeekNo(),
                        row.microSkills().stream().map(s -> bounded(s,160)).toList(),
                        row.assigned(), row.answered(), row.correct(), row.availability(), row.nextActions())).toList();
                String output = response.page(rows,page.hasMore(),page.nextCursor(),page.coverage().name(),
                        page.asOf(),limit,Map.of("continuationScope","TURN_ONLY"));
                return output;
            }
            var zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
            var extra = Map.<String,Object>of("fromInclusive",scope.weekStart(),"toExclusive",scope.asOf(),
                    "timeZone",zone.getId(),"weekStartLocalDate",scope.weekStart().atZone(zone).toLocalDate().toString(),
                    "continuationScope","TURN_ONLY");
            if (scope.weekStart().equals(scope.asOf())) {
                if (cursor != null) return response.invalid();
                return response.page(List.of(),false,null,"COMPLETE",scope.asOf(),limit,extra);
            }
            var page = paths.recent(owner,scope.weekStart(),scope.asOf(),query);
            if (page.coverage() == null || !scope.asOf().equals(page.asOf()) || page.items().stream().anyMatch(row ->
                    !validDayStatus(row.dayStatus()) || row.eventKind() == null
                    || row.weekNo() < 1 || row.dayNo() < 1
                    || !validRate(row.completionRate()) || !validRate(row.practiceScore())
                    || row.occurredAt() == null || row.occurredAt().isBefore(scope.weekStart())
                    || !row.occurredAt().isBefore(scope.asOf())))
                return response.error("INCONSISTENT_STATE");
            var rows = page.items().stream().map(row -> fields("pathId",row.pathId(),"weekNo",row.weekNo(),
                    "dayNo",row.dayNo(),"dayStatus",row.dayStatus(),"eventKind",row.eventKind(),
                    "occurredAt",row.occurredAt(),"localDate",row.occurredAt().atZone(zone).toLocalDate().toString(),
                    "closeReason",row.closeReason(),"completionRate",row.completionRate(),"practiceScore",row.practiceScore())).toList();
            String output = response.page(rows,page.hasMore(),page.nextCursor(),page.coverage().name(),
                    page.asOf(),limit,extra);
            return output;
        } catch (InvalidArguments invalid) {
            return response.json(Map.of("status", "INVALID_ARGUMENTS", "message",
                    "Use mode ACTIVE, COUNTS or RECENT. For COUNTS send mode alone. For ACTIVE/RECENT omit optional fields unless needed; use only a returned same-mode nextCursor."));
        } catch (LearningPathCompanionReadException failure) {
            return switch (failure.getReason()) {
                case INVALID_QUERY -> response.invalid();
                case INCONSISTENT_STATE -> response.error("INCONSISTENT_STATE");
                case UNAVAILABLE -> response.error("DEPENDENCY_UNAVAILABLE");
            };
        } catch (RuntimeException unavailable) {
            return response.error("DEPENDENCY_UNAVAILABLE");
        }
    }
}
