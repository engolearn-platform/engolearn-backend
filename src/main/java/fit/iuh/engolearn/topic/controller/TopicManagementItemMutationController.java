package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.TopicItemCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemOrderRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVisibilityRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemsMutationResponse;
import fit.iuh.engolearn.topic.service.TopicManagementItemMutationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/management/topics/{topicId}/items")
@RequiredArgsConstructor
public class TopicManagementItemMutationController {
    private final TopicManagementItemMutationService mutations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopicItemsMutationResponse> create(@PathVariable String topicId,
                                                           @RequestBody TopicItemCreateRequest body) {
        return ApiResponse.ok(mutations.create(topicId, body));
    }

    @PatchMapping("/{itemId}")
    public ApiResponse<TopicItemsMutationResponse> update(@PathVariable String topicId,
                                                           @PathVariable String itemId,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(mutations.update(topicId, itemId, body));
    }

    @PatchMapping("/order")
    public ApiResponse<TopicItemsMutationResponse> reorder(@PathVariable String topicId,
                                                            @RequestBody TopicItemOrderRequest body) {
        return ApiResponse.ok(mutations.reorder(topicId, body));
    }

    @PatchMapping("/{itemId}/visibility")
    public ApiResponse<TopicItemsMutationResponse> visibility(@PathVariable String topicId,
                                                               @PathVariable String itemId,
                                                               @RequestBody TopicItemVisibilityRequest body) {
        return ApiResponse.ok(mutations.visibility(topicId, itemId, body));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<TopicItemsMutationResponse> delete(@PathVariable String topicId,
                                                           @PathVariable String itemId,
                                                           @RequestParam Long revision) {
        return ApiResponse.ok(mutations.delete(topicId, itemId, revision));
    }
}
