package fit.iuh.engolearn.grammar.controller;

import fit.iuh.engolearn.grammar.dto.request.GrammarSeenRequest;
import fit.iuh.engolearn.grammar.dto.request.GrammarStartPracticeRequest;
import fit.iuh.engolearn.grammar.dto.request.GrammarSubmitAnswerRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarAnswerResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarLessonResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarPracticeResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarRoadmapResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarSeenResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicDetailResponse;
import fit.iuh.engolearn.grammar.service.GrammarLearnerService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grammar")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class GrammarLearnerController {
    private static final String TOPIC = "/topics/{topicId}";
    private static final String LESSON = TOPIC + "/lessons/{lessonId}";
    private static final String PRACTICE = "/practice/{sessionId}";
    private final GrammarLearnerService service;

    @GetMapping("/roadmap")
    public ApiResponse<GrammarRoadmapResponse> roadmap() {
        return ApiResponse.ok(service.roadmap());
    }

    @GetMapping(TOPIC)
    public ApiResponse<GrammarTopicDetailResponse> topic(@PathVariable String topicId) {
        return ApiResponse.ok(service.topic(topicId));
    }

    @GetMapping(LESSON)
    public ApiResponse<GrammarLessonResponse> lesson(@PathVariable String topicId, @PathVariable String lessonId) {
        return ApiResponse.ok(service.lesson(topicId, lessonId));
    }

    @PostMapping(LESSON + "/seen")
    public ApiResponse<GrammarSeenResponse> markSeen(@PathVariable String topicId, @PathVariable String lessonId,
                                               @Valid @RequestBody GrammarSeenRequest request) {
        return ApiResponse.ok(service.markSeen(topicId, lessonId, request.contentVersion()));
    }

    @PostMapping(LESSON + "/practice")
    public ApiResponse<GrammarPracticeResponse> startPractice(@PathVariable String topicId, @PathVariable String lessonId,
                                                         @Valid @RequestBody(required = false) GrammarStartPracticeRequest request) {
        return ApiResponse.ok(service.startPractice(topicId, lessonId, request != null && Boolean.TRUE.equals(request.restart())));
    }

    @GetMapping(PRACTICE)
    public ApiResponse<GrammarPracticeResponse> practice(@PathVariable String sessionId) {
        return ApiResponse.ok(service.practice(sessionId));
    }

    @PostMapping(PRACTICE + "/answers")
    public ApiResponse<GrammarAnswerResponse> submitAnswer(@PathVariable String sessionId,
                                                    @Valid @RequestBody GrammarSubmitAnswerRequest request) {
        return ApiResponse.ok(service.submitAnswer(sessionId, request));
    }
}
