package fit.iuh.engolearn.learning.progress.controller;

import fit.iuh.engolearn.content.api.TopicFilter;
import fit.iuh.engolearn.learning.progress.dto.ExploreTopicItemResponse;
import fit.iuh.engolearn.learning.progress.dto.TopicDetailWithProgressResponse;
import fit.iuh.engolearn.learning.progress.service.ExploreService;
import fit.iuh.engolearn.shared.model.CefrLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/topics")
public class ExploreController {

    private final ExploreService exploreService;

    public ExploreController(ExploreService exploreService) {
        this.exploreService = exploreService;
    }

    @GetMapping
    public Page<ExploreTopicItemResponse> explore(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 20, sort = "orderIndex") Pageable pageable) {
        return exploreService.explore(new TopicFilter(parseLevel(level), category), userId, pageable);
    }

    @GetMapping("/{topicId}")
    public TopicDetailWithProgressResponse getDetail(
            @PathVariable String topicId,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) String userId) {
        return exploreService
                .getDetail(topicId, version, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
    }

    private CefrLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return CefrLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid level: " + level);
        }
    }
}
