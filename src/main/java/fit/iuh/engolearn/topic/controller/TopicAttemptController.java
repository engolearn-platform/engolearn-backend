package fit.iuh.engolearn.topic.controller;

import lombok.RequiredArgsConstructor;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.topic.dto.request.*;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.service.TopicAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@PreAuthorize("hasAnyRole('USER', 'CONTENT_MANAGER', 'ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TopicAttemptController {
    private final TopicAttemptService attempts;

    @PostMapping("/topic-items/{itemId}/attempts")
    public ApiResponse<TopicAttemptResponse> start(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String itemId, @Valid @RequestBody StartTopicAttemptRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.start(principal.getId(), itemId, body, key));
    }

    @GetMapping("/topic-attempts/{attemptId}")
    public ApiResponse<TopicAttemptResponse> get(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.get(principal.getId(), attemptId));
    }

    @GetMapping("/topic-attempts/{attemptId}/content")
    public ApiResponse<TopicItemContentResponse> content(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.content(principal.getId(), attemptId));
    }

    @PostMapping("/topic-attempts/{attemptId}/advance")
    public ApiResponse<TopicAttemptResponse> advance(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId, @Valid @RequestBody AdvanceTopicContentRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.advance(principal.getId(), attemptId, body, key));
    }

    @PostMapping("/topic-attempts/{attemptId}/access")
    public ApiResponse<Map<String, Instant>> access(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.access(principal.getId(), attemptId, key));
    }

    @GetMapping("/topic-attempts/{attemptId}/quiz-navigation")
    public ApiResponse<QuizNavigationResponse> quizNavigation(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.quizNavigation(principal.getId(), attemptId));
    }

    @GetMapping("/topic-attempts/{attemptId}/quiz")
    public ApiResponse<CurrentQuizResponse> currentQuiz(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.getCurrentQuiz(principal.getId(), attemptId));
    }

    @PostMapping("/topic-attempts/{attemptId}/quizzes/{quizId}/answers")
    public ApiResponse<QuizAnswerResponse> answerQuiz(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId, @PathVariable String quizId,
            @Valid @RequestBody AnswerQuizRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.answerQuiz(principal.getId(), attemptId, quizId, body, key));
    }

    @PostMapping("/topic-attempts/{attemptId}/completion")
    public ApiResponse<TopicAttemptResponse> complete(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId,
            @Valid @RequestBody CompleteTopicAttemptRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.complete(principal.getId(), attemptId, body, key));
    }

    @GetMapping("/topic-attempts/{attemptId}/result")
    public ApiResponse<TopicItemResultResponse> itemResult(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.itemResult(principal.getId(), attemptId));
    }

    @GetMapping("/topics/{topicId}/result")
    public ApiResponse<TopicResultResponse> topicResult(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String topicId) {
        return ApiResponse.ok(attempts.topicResult(principal.getId(), topicId));
    }

    @GetMapping("/topic-attempts/{attemptId}/review")
    public ApiResponse<TopicItemReviewResponse> review(@AuthenticationPrincipal AuthenticatedUserPrincipal principal, @PathVariable String attemptId) {
        return ApiResponse.ok(attempts.review(principal.getId(), attemptId));
    }
}
