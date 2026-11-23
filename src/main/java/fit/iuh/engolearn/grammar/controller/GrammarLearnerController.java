package fit.iuh.engolearn.grammar.controller;

import fit.iuh.engolearn.grammar.dto.GrammarLearnerRequests.SeenRequest;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerRequests.StartPracticeRequest;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerRequests.SubmitAnswerRequest;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.AnswerResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.LessonResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.PracticeResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.RoadmapResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.SeenResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.TopicDetailResponse;
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
    public ApiResponse<RoadmapResponse> roadmap() {
        return ApiResponse.ok(service.roadmap());
    }

    @GetMapping(TOPIC)
    public ApiResponse<TopicDetailResponse> topic(@PathVariable String topicId) {
        return ApiResponse.ok(service.topic(topicId));
    }

    @GetMapping(LESSON)
    public ApiResponse<LessonResponse> lesson(@PathVariable String topicId, @PathVariable String lessonId) {
        return ApiResponse.ok(service.lesson(topicId, lessonId));
    }

    @PostMapping(LESSON + "/seen")
    public ApiResponse<SeenResponse> markSeen(@PathVariable String topicId, @PathVariable String lessonId,
                                               @Valid @RequestBody SeenRequest request) {
        return ApiResponse.ok(service.markSeen(topicId, lessonId, request.contentVersion()));
    }

    @PostMapping(LESSON + "/practice")
    public ApiResponse<PracticeResponse> startPractice(@PathVariable String topicId, @PathVariable String lessonId,
                                                         @Valid @RequestBody(required = false) StartPracticeRequest request) {
        return ApiResponse.ok(service.startPractice(topicId, lessonId, request != null && Boolean.TRUE.equals(request.restart())));
    }

    @GetMapping(PRACTICE)
    public ApiResponse<PracticeResponse> practice(@PathVariable String sessionId) {
        return ApiResponse.ok(service.practice(sessionId));
    }

    @PostMapping(PRACTICE + "/answers")
    public ApiResponse<AnswerResponse> submitAnswer(@PathVariable String sessionId,
                                                    @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(service.submitAnswer(sessionId, request));
    }
}
