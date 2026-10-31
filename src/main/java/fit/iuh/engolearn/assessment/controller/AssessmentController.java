package fit.iuh.engolearn.assessment.controller;

import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import fit.iuh.engolearn.assessment.service.AssessmentService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/api/v1/assessment")
public class AssessmentController {
    private final AssessmentService service;private final CurrentUserProvider users;
    @ModelAttribute void noCache(HttpServletResponse response) {response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");}
    @GetMapping("/preparation") public ApiResponse<AssessmentPreparationResponse> preparation() {return ApiResponse.ok(service.preparation(user()));}
    @GetMapping("/sound-check") public ApiResponse<AssessmentMediaResponse> soundCheck() {user();return ApiResponse.ok(service.soundCheck());}
    @PostMapping("/attempts") public ApiResponse<AssessmentStateResponse> start(@Valid @RequestBody AssessmentStartRequest request) {return ApiResponse.ok(service.start(user(),request));}
    @GetMapping("/attempts") public ApiResponse<List<AssessmentHistoryItemResponse>> history(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return ApiResponse.ok(service.history(user(),page,size));}
    @GetMapping("/attempts/{id}") public ApiResponse<AssessmentStateResponse> state(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding) {return ApiResponse.ok(service.state(user(),id,binding));}
    @PutMapping("/attempts/{id}/answer") public ApiResponse<AssessmentStateResponse> answer(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding,@Valid @RequestBody AssessmentAnswerRequest request) {return ApiResponse.ok(service.answer(user(),id,binding,request,false));}
    @PostMapping("/attempts/{id}/next") public ApiResponse<AssessmentStateResponse> next(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding,@Valid @RequestBody AssessmentAnswerRequest request) {return ApiResponse.ok(service.answer(user(),id,binding,request,true));}
    @PostMapping("/attempts/{id}/submit") public ApiResponse<AssessmentStateResponse> submit(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding,@Valid @RequestBody AssessmentConfirmRequest request) {return ApiResponse.ok(service.submit(user(),id,binding));}
    @PostMapping("/attempts/{id}/abandon") public ApiResponse<AssessmentStateResponse> abandon(@PathVariable String id,@Valid @RequestBody AssessmentConfirmRequest request) {return ApiResponse.ok(service.abandon(user(),id));}
    @PostMapping("/attempts/{id}/audio") public ApiResponse<AssessmentMediaResponse> audio(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding,@Valid @RequestBody AssessmentAudioRequest request) {return ApiResponse.ok(service.audio(user(),id,binding,request));}
    @GetMapping("/attempts/{id}/result") public ApiResponse<AssessmentResultResponse> result(@PathVariable String id) {return ApiResponse.ok(service.result(user(),id));}
    @GetMapping("/attempts/{id}/review") public ApiResponse<AssessmentReviewResponse> review(@PathVariable String id) {return ApiResponse.ok(service.review(user(),id));}
    @GetMapping("/attempts/{id}/review/audio/{index}") public ApiResponse<AssessmentMediaResponse> reviewAudio(@PathVariable String id,@PathVariable @Min(0) @Max(25) int index) {return ApiResponse.ok(service.reviewAudio(user(),id,index));}
    private String user() {return users.requireCurrentUserId();}
    @PostMapping("/attempts/{id}/audio-failure")
    public ApiResponse<AssessmentStateResponse> audioFailure(@PathVariable String id,@RequestHeader("X-Assessment-Binding") String binding,
            @Valid @RequestBody AssessmentAudioFailureRequest request) {
        return ApiResponse.ok(service.reportAudioFailure(user(),id,binding,request));
    }
}
