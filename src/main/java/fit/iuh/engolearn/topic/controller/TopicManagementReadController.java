package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/topics")
@RequiredArgsConstructor
public class TopicManagementReadController {
    private final TopicManagementReadService service;

    @GetMapping
    public ApiResponse<TopicManagementListResponse> list(@RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String cefr,
                                                          @RequestParam(required = false) String category,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(new TopicManagementListQuery(search, cefr, category, status, page, size)));
    }

    @GetMapping("/{topicId}")
    public ApiResponse<TopicManagementResponse> topic(@PathVariable String topicId) {
        return ApiResponse.ok(service.topic(topicId));
    }

    @GetMapping("/{topicId}/items")
    public ApiResponse<List<TopicManagementItemSummary>> items(@PathVariable String topicId) {
        return ApiResponse.ok(service.items(topicId));
    }

    @GetMapping("/{topicId}/items/{itemId}")
    public ApiResponse<TopicManagementItemDetail> item(@PathVariable String topicId, @PathVariable String itemId,
                                                        @RequestParam(required = false) String view) {
        return ApiResponse.ok("published".equals(view)
                ? service.publishedItem(topicId, itemId) : service.item(topicId, itemId));
    }
}
