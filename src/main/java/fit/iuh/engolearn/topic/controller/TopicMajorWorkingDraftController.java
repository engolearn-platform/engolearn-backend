package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.TopicItemWorkingDraftRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;
import fit.iuh.engolearn.topic.service.TopicMajorWorkingDraftService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/topics/{topicId}/items/{itemId}/working-draft")
@RequiredArgsConstructor
public class TopicMajorWorkingDraftController {
    private final TopicMajorWorkingDraftService service;

    @PostMapping
    public ApiResponse<TopicItemResourceMutationResponse> open(@PathVariable String topicId,
                                                                @PathVariable String itemId,
                                                                @Valid @RequestBody TopicItemWorkingDraftRequest request) {
        return ApiResponse.ok(service.open(topicId, itemId, request));
    }
}
