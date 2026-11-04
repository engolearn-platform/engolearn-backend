package fit.iuh.engolearn.topic.controller;

import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.StartTopicRetryRequest;
import fit.iuh.engolearn.topic.dto.response.TopicRetrySessionResponse;
import fit.iuh.engolearn.topic.service.TopicRetryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@PreAuthorize("hasAnyRole('USER', 'CONTENT_MANAGER', 'ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TopicRetryController {
    private final TopicRetryService retries;

    @PostMapping("/topics/{topicId}/retry-sessions")
    public ApiResponse<TopicRetrySessionResponse> start(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String topicId,@Valid @RequestBody StartTopicRetryRequest body,
            @NotBlank @Size(max=128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(retries.startTopic(principal.getId(), topicId,body,key));
    }

    @GetMapping("/topic-retry-sessions/{retrySessionId}")
    public ApiResponse<TopicRetrySessionResponse> get(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String retrySessionId) {
        return ApiResponse.ok(retries.getSession(principal.getId(), retrySessionId));
    }
}
