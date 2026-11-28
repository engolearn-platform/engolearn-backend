package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.service.*;
import fit.iuh.engolearn.topic.contract.TopicCompanionReads;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.contract.exception.TopicCompanionInvalidQueryException;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import static fit.iuh.engolearn.companion.service.impl.BusinessToolResponse.*;

@Component
public class CompanionTopicToolHandler implements CompanionToolHandler {
    private final TopicCompanionReads topics;
    private final BusinessToolResponse response;

    public CompanionTopicToolHandler(TopicCompanionReads topics, ObjectMapper mapper) {
        this.topics = topics;
        this.response = new BusinessToolResponse(mapper);
    }

    @Override public List<CompanionToolDefinition> definitions() {
        return List.of(
                new CompanionToolDefinition("findLearningTopics",
                        "Read-only catalog search for eligible published topics the learner has NOT started and does not already own. Excludes in-progress and completed topics. level selects exactly one CEFR level (default: learner's current level); lower levels are eligible, higher levels are not. keyword is an optional case-insensitive literal substring of the English or Vietnamese topic TITLE, not its description, skills or semantic meaning; omission searches all titles within the selected level. Returns topic titles, level, description snippet, visible item count and first eligible item if present; default three rows, maximum five. NO_DATA is specific to the requested level and keyword, not the whole catalog or the learner's study history.",
                        """
                        {"type":"object","properties":{"keyword":{"type":"string","minLength":1,"maxLength":80,"description":"Optional short literal keyword. Omit entirely for browsing or broadened searches; never pass blank or null."},"level":{"type":"string","enum":["A1","A2","B1","B2","C1","C2"],"description":"Exact CEFR filter. Omit for current learner level; use explicit lower CEFR for review."},"limit":{"type":"integer","minimum":1,"maximum":5},"cursor":{"type":"string","maxLength":512,"description":"Only a returned continuation for the same search."}},"additionalProperties":false}
                        """),
                new CompanionToolDefinition("getTopicLearningProgress",
                        "Read-only progress for Topic Learning. RESUME returns in-progress topics only, most recently accessed first, with CEFR, completed/total visible items, last access, active item attempt and next eligible item when present; default three rows, maximum five. It does not locate an exact saved position or discover unstarted topics. COUNTS returns two independent totals across current visible topic generations: completed topics and completed items (including items in unfinished topics); valid input is mode=COUNTS alone, without limit or cursor. Counts exclude obsolete generations and do not cover Learning Paths or other modules. Neither mode supplies dated activity history.",
                        """
                        {"type":"object","properties":{"mode":{"type":"string","enum":["RESUME","COUNTS"],"description":"RESUME for unfinished topics, COUNTS for independent completion totals."},"limit":{"type":"integer","minimum":1,"maximum":5,"description":"Optional for RESUME only."},"cursor":{"type":"string","maxLength":512,"description":"Returned RESUME continuation only; omit for COUNTS."}},"required":["mode"],"additionalProperties":false}
                        """));
    }

    private static TopicDiscoveryPageDto.ItemDto item(TopicDiscoveryPageDto.ItemDto item) {
        return item == null ? null : new TopicDiscoveryPageDto.ItemDto(item.itemId(),bounded(item.title(),160),item.startAllowed());
    }

    @Override public String execute(String name, Map<String, Object> input, CompanionToolExecutionContext context) {
        String owner = context.learnerId();
        if (owner == null || owner.isBlank()) return response.forbidden();
        try {
            if ("findLearningTopics".equals(name)) {
                var a = args(input, "keyword", "level", "limit", "cursor");
                var keyword = text(a, "keyword", 80, null);
                var level = a.containsKey("level") ? CefrLevel.valueOf(choice(a, "level", null,
                        "A1","A2","B1","B2","C1","C2")) : null;
                int limit = limit(a, 1, 3);
                var page = topics.discover(owner, new TopicDiscoveryQueryDto(
                        keyword == null ? null : keyword.trim(), level, limit, text(a,"cursor",512,null)));
                if (page.items().stream().anyMatch(row -> row.visibleItemCount() < 0
                        || (row.firstEligibleItem() != null && (!row.firstEligibleItem().startAllowed() || row.visibleItemCount() == 0))))
                    return response.error("INCONSISTENT_STATE");
                var rows = page.items().stream().map(row -> new TopicDiscoveryPageDto.RowDto(
                        row.topicId(), bounded(row.titleEn(),160), bounded(row.titleVi(),160), row.cefrLevel(),
                        bounded(row.descriptionSnippet(),240), row.visibleItemCount(), item(row.firstEligibleItem()))).toList();
                return response.page(rows, page.hasMore(), page.nextCursor(), page.coverage(),
                        page.asOf(), limit, Map.of("searchScope","ELIGIBLE_UNSTARTED_TOPICS",
                                "levelFilter",level == null ? "CURRENT_LEARNER_LEVEL" : level.name(),
                                "keywordFiltered",keyword != null));
            }
            if (!"getTopicLearningProgress".equals(name)) return response.error("UNSUPPORTED");
            var a = args(input, "mode", "limit", "cursor");
            var mode = choice(a, "mode", null, "RESUME", "COUNTS");
            if ("COUNTS".equals(mode)) {
                countsOnly(a);
                var counts = topics.counts(owner);
                if (!"COMPLETE".equals(counts.coverage()) || counts.asOf() == null
                        || !"CURRENT_VISIBLE_CURRENT_GENERATION".equals(counts.scope())
                        || counts.completedTopics() < 0 || counts.completedTopicItems() < 0)
                    return response.error("INCONSISTENT_STATE");
                return response.success(counts);
            }
            int limit = limit(a, 1, 3);
            var page = topics.resume(owner, new TopicPageQueryDto(limit, text(a,"cursor",512,null)));
            if (page.items().stream().anyMatch(row -> !"IN_PROGRESS".equals(row.learningStatus())
                    || row.completedVisibleItems() < 0 || row.totalVisibleItems() < row.completedVisibleItems()
                    || (row.nextEligibleItem() != null && !row.nextEligibleItem().startAllowed())))
                return response.error("INCONSISTENT_STATE");
            var rows = page.items().stream().map(row -> new TopicResumePageDto.RowDto(
                    row.topicId(),bounded(row.titleEn(),160),bounded(row.titleVi(),160),row.cefrLevel(),
                    row.learningStatus(),row.completedVisibleItems(),row.totalVisibleItems(),row.lastAccessedAt(),
                    row.activeLearningItem() == null ? null : new TopicResumePageDto.ActiveItemDto(
                            row.activeLearningItem().itemId(),bounded(row.activeLearningItem().title(),160),
                            row.activeLearningItem().attemptId()),item(row.nextEligibleItem()))).toList();
            return response.page(rows, page.hasMore(), page.nextCursor(), page.coverage(),
                    page.asOf(), limit, Map.of());
        } catch (InvalidArguments | TopicCompanionInvalidQueryException invalid) {
            return response.invalid();
        } catch (RuntimeException unavailable) {
            return response.error("DEPENDENCY_UNAVAILABLE");
        }
    }
}
