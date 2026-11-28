package fit.iuh.engolearn.companion.controller;

import fit.iuh.engolearn.companion.dto.response.ConversationPageResponse;
import fit.iuh.engolearn.companion.dto.response.ConversationResponse;
import fit.iuh.engolearn.companion.dto.response.MessagePageResponse;
import fit.iuh.engolearn.companion.service.CompanionConversationService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companion/conversations")
public class CompanionConversationController {
    private final CompanionConversationService conversations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> create() {
        return ApiResponse.ok(conversations.create());
    }

    @GetMapping
    public ApiResponse<ConversationPageResponse> list(
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(conversations.list(before, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@PathVariable String conversationId) {
        return ApiResponse.ok(conversations.get(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@PathVariable String conversationId) {
        conversations.delete(conversationId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<MessagePageResponse> messages(
            @PathVariable String conversationId,
            @RequestParam(required = false) @Positive Long beforeSequence,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(conversations.messages(conversationId, beforeSequence, limit));
    }

    @ModelAttribute
    void noCache(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
