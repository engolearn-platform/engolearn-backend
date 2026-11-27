package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.TopicPublishRequest;
import fit.iuh.engolearn.topic.dto.response.TopicPublishResponse;
import fit.iuh.engolearn.topic.dto.response.TopicUnpublishResponse;
import fit.iuh.engolearn.topic.service.TopicPublishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/topics")
@RequiredArgsConstructor
public class TopicManagementPublishController {
    private final TopicPublishService service;

    @PostMapping("/{topicId}/publish")
    public ApiResponse<TopicPublishResponse> publish(@PathVariable String topicId,
                                                      @Valid @RequestBody TopicPublishRequest request) {
        return ApiResponse.ok(service.publish(topicId, request));
    }

    @PostMapping("/{topicId}/unpublish")
    public ApiResponse<TopicUnpublishResponse> unpublish(@PathVariable String topicId,
                                                          @Valid @RequestBody TopicPublishRequest request) {
        return ApiResponse.ok(service.unpublish(topicId, request));
    }
}