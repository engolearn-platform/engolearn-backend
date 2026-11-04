package fit.iuh.engolearn.topic.controller;

import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;
import fit.iuh.engolearn.topic.service.TopicLearnerCatalogService;
import fit.iuh.engolearn.topic.dto.response.MyTopicPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasAnyRole('USER', 'CONTENT_MANAGER', 'ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/topics")
public class TopicBrowseController {
    private final TopicLearnerCatalogService topicBrowseService;

    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<TopicPageResponse> getAvailableTopics(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        GetAvailableTopicRequest request = GetAvailableTopicRequest.builder()
                .level(level)
                .keyword(keyword)
                .sort(sort)
                .limit(limit)
                .cursor(cursor)
                .build();
        return ApiResponse.ok(topicBrowseService.discover(principal == null ? null : principal.getId(), request));
    }

    @GetMapping("/mine")
    public ApiResponse<MyTopicPageResponse> getMyTopics(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(name = "status", defaultValue = "IN_PROGRESS") String status,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ApiResponse.ok(topicBrowseService.mine(principal.getId(), status, limit, cursor));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{topicId}")
    public ApiResponse<TopicDetailResponse> getTopicDetail(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable("topicId") String topicId) {
        return ApiResponse.ok(topicBrowseService.detail(principal == null ? null : principal.getId(), topicId));
    }
}
