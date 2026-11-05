package fit.iuh.engolearn.topic.controller;

import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.ResetTopicRequest;
import fit.iuh.engolearn.topic.dto.response.ResetTopicResponse;
import fit.iuh.engolearn.topic.service.TopicResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@PreAuthorize("hasAnyRole('USER', 'CONTENT_MANAGER', 'ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TopicResetController {
    private final TopicResetService resets;

    @PostMapping("/topics/{topicId}/reset")
    public ApiResponse<ResetTopicResponse> reset(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String topicId, @Valid @RequestBody ResetTopicRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(resets.reset(principal.getId(), topicId, body, key));
    }
}
