package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.*;
import fit.iuh.engolearn.speaking.dto.response.SpeakingAssessmentResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.speaking.service.SpeakingSessions;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/speaking")
@RequiredArgsConstructor
public class SpeakingController {
    private final SpeakingSessions sessions;

    @GetMapping("/availability")
    public ApiResponse<Availability> availability(@RequestParam(required = false) SpeakingScopeType scopeType,
                                                  @RequestParam(required = false) String topicId,
                                                  @RequestParam(required = false) String topicItemId) {
        return ApiResponse.ok(sessions.availability(new SpeakingScopeDto(scopeType, topicId, topicItemId)));
    }

    @PostMapping("/sessions")
    public ApiResponse<Session> create(@Valid @RequestBody Create request, HttpServletResponse response) {
        var result = sessions.create(request);
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.session());
    }

    @PostMapping("/sessions/replace")
    public ApiResponse<?> replace(@Valid @RequestBody Replace request, HttpServletResponse response) {
        var result = sessions.replace(request);
        if (result.replacingSessionId() != null) {
            response.setStatus(202);
            return ApiResponse.ok(new Replacing("REPLACING", result.replacingSessionId()));
        }
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.session());
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Session> read(@PathVariable String id) {
        return ApiResponse.ok(sessions.read(id));
    }

    @PostMapping("/sessions/{id}/resume")
    public ApiResponse<Session> resume(@PathVariable String id, @Valid @RequestBody Resume request) {
        return ApiResponse.ok(sessions.resume(id, request));
    }

    @PostMapping("/sessions/{id}/socket-ticket")
    public ApiResponse<Ticket> ticket(@PathVariable String id, @Valid @RequestBody Control request) {
        return ApiResponse.ok(sessions.ticket(id, request.controllerEpoch()));
    }

    @PostMapping("/sessions/{id}/end")
    public ApiResponse<Session> end(@PathVariable String id, @Valid @RequestBody Control request) {
        return ApiResponse.ok(sessions.end(id, request.controllerEpoch()));
    }

    @GetMapping("/sessions")
    public ApiResponse<Page<Session>> history(@RequestParam(required = false) SpeakingScopeType scopeType,
                                              @RequestParam(required = false) String topicId,
                                              @RequestParam(required = false) String topicItemId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        SpeakingScopeDto scope = scopeType == null && topicId == null && topicItemId == null
                ? null : new SpeakingScopeDto(scopeType, topicId, topicItemId);
        return ApiResponse.ok(sessions.history(scope, page, size));
    }

    @GetMapping("/sessions/{id}/turns")
    public ApiResponse<Page<Turn>> turns(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(sessions.turns(id, page, size));
    }

    @GetMapping("/sessions/{id}/assessment")
    public ApiResponse<SpeakingAssessmentResponse> assessment(@PathVariable String id) {
        return ApiResponse.ok(sessions.assessment(id));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable String id) {
        sessions.delete(id);
        return null;
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteAll() {
        sessions.deleteAll();
        return null;
    }

    @GetMapping("/sessions/{id}/turns/{turnId}/audio/{kind}")
    public ApiResponse<Audio> turnAudio(@PathVariable String id, @PathVariable String turnId, @PathVariable String kind) {
        return ApiResponse.ok(sessions.turnAudio(id, turnId, kind));
    }

    @GetMapping("/sessions/{id}/opening/audio")
    public ApiResponse<Audio> openingAudio(@PathVariable String id) {
        return ApiResponse.ok(sessions.openingAudio(id));
    }
}
