package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.topic.dto.request.TopicCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.request.TopicUpdateRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.service.TopicManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Management topic surface (design record F05 §4) — TPL-UC-010 + TPL-UC-019.
 * HTTP binding only; all behavior lives in {@link TopicManagementService}.
 *
 * <p>Base path {@code /api/management/topics} (management surface, not a role);
 * the learner surface {@code /api/topics} is a separate, future feature.</p>
 */
@RestController
@RequestMapping("/api/management/topics")
public class TopicManagementController {

    private final TopicManagementService topicManagementService;

    public TopicManagementController(TopicManagementService topicManagementService) {
        this.topicManagementService = topicManagementService;
    }

    /** E1 — create a draft; 201 + {@code Location} + body. */
    @PostMapping
    public ResponseEntity<TopicManagementResponse> create(@Valid @RequestBody TopicCreateRequest request) {
        TopicManagementResponse created = topicManagementService.create(request);
        return ResponseEntity.created(URI.create("/api/management/topics/" + created.id())).body(created);
    }

    /** E2 — read one topic. */
    @GetMapping("/{id}")
    public TopicManagementResponse get(@PathVariable String id) {
        return topicManagementService.get(id);
    }

    /** E3 — partial update in place. */
    @PatchMapping("/{id}")
    public TopicManagementResponse update(@PathVariable String id,
                                          @Valid @RequestBody TopicUpdateRequest request) {
        return topicManagementService.update(id, request);
    }

    /** E4 — management list: search + filters + paging + counters. */
    @GetMapping
    public TopicManagementListResponse list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "cefr", required = false) String cefr,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return topicManagementService.list(new TopicManagementListQuery(search, cefr, category, status, page, size));
    }
}
