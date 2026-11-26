package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.TopicItemContextRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVocabularyRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemExpressionsRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemQuizzesRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemMinorRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;
import fit.iuh.engolearn.topic.service.TopicManagementResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/topics/{topicId}/items/{itemId}")
@RequiredArgsConstructor
public class TopicManagementResourceController {
    private final TopicManagementResourceService service;

    @PutMapping("/context")
    public ApiResponse<TopicItemResourceMutationResponse> context(@PathVariable String topicId,
                                                                   @PathVariable String itemId,
                                                                   @RequestBody TopicItemContextRequest request) {
        return ApiResponse.ok(service.context(topicId, itemId, request));
    }

    @PutMapping("/vocabulary")
    public ApiResponse<TopicItemResourceMutationResponse> vocabulary(@PathVariable String topicId,
                                                                      @PathVariable String itemId,
                                                                      @RequestBody TopicItemVocabularyRequest request) {
        return ApiResponse.ok(service.vocabulary(topicId, itemId, request));
    }

    @PutMapping("/expressions")
    public ApiResponse<TopicItemResourceMutationResponse> expressions(@PathVariable String topicId,
                                                                        @PathVariable String itemId,
                                                                        @RequestBody TopicItemExpressionsRequest request) {
        return ApiResponse.ok(service.expressions(topicId, itemId, request));
    }

    @PutMapping("/quizzes")
    public ApiResponse<TopicItemResourceMutationResponse> quizzes(@PathVariable String topicId,
                                                                    @PathVariable String itemId,
                                                                    @RequestBody TopicItemQuizzesRequest request) {
        return ApiResponse.ok(service.quizzes(topicId, itemId, request));
    }

    @PatchMapping("/minor")
    public ApiResponse<TopicItemResourceMutationResponse> minor(@PathVariable String topicId,
                                                                  @PathVariable String itemId,
                                                                  @RequestBody TopicItemMinorRequest request) {
        return ApiResponse.ok(service.minor(topicId, itemId, request));
    }
}
