package fit.iuh.engolearn.topic.controller;

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

@RestController
@RequestMapping("/api/v1")
public class TopicAttemptController {
    private final TopicAttemptService attempts;
    public TopicAttemptController(TopicAttemptService attempts) { this.attempts = attempts; }

    @PostMapping("/topic-items/{itemId}/attempts")
    public ApiResponse<TopicAttemptResponse> start(@PathVariable String itemId, @Valid @RequestBody StartTopicAttemptRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.start(itemId, body, key));
    }

    @GetMapping("/topic-attempts/{attemptId}")
    public ApiResponse<TopicAttemptResponse> get(@PathVariable String attemptId) {
        return ApiResponse.ok(attempts.get(attemptId));
    }

    @GetMapping("/topic-attempts/{attemptId}/content")
    public ApiResponse<TopicItemContentResponse> content(@PathVariable String attemptId) {
        return ApiResponse.ok(attempts.content(attemptId));
    }

    @PostMapping("/topic-attempts/{attemptId}/advance")
    public ApiResponse<TopicAttemptResponse> advance(@PathVariable String attemptId, @Valid @RequestBody AdvanceTopicContentRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.advance(attemptId, body, key));
    }

    @PostMapping("/topic-attempts/{attemptId}/access")
    public ApiResponse<Map<String, Instant>> access(@PathVariable String attemptId,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.access(attemptId, key));
    }

    @GetMapping("/topic-attempts/{attemptId}/quiz")
    public ApiResponse<CurrentQuizResponse> currentQuiz(@PathVariable String attemptId) {
        return ApiResponse.ok(attempts.getCurrentQuiz(attemptId));
    }

    @PostMapping("/topic-attempts/{attemptId}/quizzes/{quizId}/answers")
    public ApiResponse<QuizAnswerResponse> answerQuiz(@PathVariable String attemptId, @PathVariable String quizId,
            @Valid @RequestBody AnswerQuizRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.answerQuiz(attemptId, quizId, body, key));
    }

    @PostMapping("/topic-attempts/{attemptId}/completion")
    public ApiResponse<TopicAttemptResponse> complete(@PathVariable String attemptId,
            @Valid @RequestBody CompleteTopicAttemptRequest body,
            @NotBlank @Size(max = 128) @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.ok(attempts.complete(attemptId, body, key));
    }

    @GetMapping("/topic-attempts/{attemptId}/result")
    public ApiResponse<TopicItemResultResponse> itemResult(@PathVariable String attemptId) {
        return ApiResponse.ok(attempts.itemResult(attemptId));
    }

    @GetMapping("/topics/{topicId}/result")
    public ApiResponse<TopicResultResponse> topicResult(@PathVariable String topicId) {
        return ApiResponse.ok(attempts.topicResult(topicId));
    }
}
