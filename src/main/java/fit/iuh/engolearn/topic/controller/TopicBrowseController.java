package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;
import fit.iuh.engolearn.topic.service.TopicBrowseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicBrowseController {
    private final TopicBrowseService topicBrowseService;

    public TopicBrowseController(TopicBrowseService topicBrowseService) {
        this.topicBrowseService = topicBrowseService;
    }

    @GetMapping
    public ApiResponse<TopicPageResponse> getAvailableTopics(
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
        return ApiResponse.ok(topicBrowseService.getAvailableTopics(request));
    }

    @GetMapping("/{topicId}")
    public ApiResponse<TopicDetailResponse> getTopicDetail(@PathVariable("topicId") String topicId) {
        GetTopicDetailRequest request = GetTopicDetailRequest.builder()
                .topicId(topicId)
                .build();
        return ApiResponse.ok(topicBrowseService.getTopicDetail(request));
    }
}
