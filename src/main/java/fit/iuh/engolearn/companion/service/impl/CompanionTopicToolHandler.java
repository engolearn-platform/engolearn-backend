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
                        "Find actual eligible published unstarted topics by optional literal keyword and CEFR. Omit level for learner's current level. No semantic matching or start action.",
                        """
                        {"type":"object","properties":{"keyword":{"type":"string","minLength":1,"maxLength":80},"level":{"type":"string","enum":["A1","A2","B1","B2","C1","C2"]},"limit":{"type":"integer","minimum":1,"maximum":5},"cursor":{"type":"string","maxLength":512}},"additionalProperties":false}
                        """),
                new CompanionToolDefinition("getTopicLearningProgress",
                        "RESUME returns current visible progress and verified active or next eligible items, not exact position. COUNTS returns separate completed topics and completed items. No dated topic history.",
                        """
                        {"type":"object","properties":{"mode":{"type":"string","enum":["RESUME","COUNTS"]},"limit":{"type":"integer","minimum":1,"maximum":5},"cursor":{"type":"string","maxLength":512}},"required":["mode"],"additionalProperties":false}
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
                var rows = page.items().stream().map(row -> new TopicDiscoveryPageDto.RowDto(
                        row.topicId(), bounded(row.titleEn(),160), bounded(row.titleVi(),160), row.cefrLevel(),
                        bounded(row.descriptionSnippet(),240), row.visibleItemCount(), item(row.firstEligibleItem()))).toList();
                return response.page(rows, page.hasMore(), page.nextCursor(), page.coverage(),
                        page.asOf(), limit, Map.of());
            }
            if (!"getTopicLearningProgress".equals(name)) return response.error("UNSUPPORTED");
            var a = args(input, "mode", "limit", "cursor");
            var mode = choice(a, "mode", null, "RESUME", "COUNTS");
            if ("COUNTS".equals(mode)) {
                countsOnly(a);
                var counts = topics.counts(owner);
                if (!"COMPLETE".equals(counts.coverage()) || counts.asOf() == null
                        || counts.completedTopics() < 0 || counts.completedTopicItems() < 0)
                    return response.error("INCONSISTENT_STATE");
                return response.success(counts);
            }
            int limit = limit(a, 1, 3);
            var page = topics.resume(owner, new TopicPageQueryDto(limit, text(a,"cursor",512,null)));
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
