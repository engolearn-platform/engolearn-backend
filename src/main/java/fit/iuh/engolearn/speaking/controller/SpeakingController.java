package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.*;
import fit.iuh.engolearn.speaking.dto.response.SpeakingAssessmentResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.Group;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.Groups;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.ParentSessions;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.service.SpeakingHistory;
import fit.iuh.engolearn.speaking.service.SpeakingSessions;
import fit.iuh.engolearn.speaking.service.SpeakingRealtime;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/speaking")
@RequiredArgsConstructor
public class SpeakingController {
    private static final Set<String> SCOPE_FILTERS = Set.of("scopeType", "topicId", "topicItemId");
    private static final Set<String> GROUP_PARAMETERS = Set.of("group", "page", "size");
    private static final Set<String> PARENT_PARAMETERS = Set.of("page", "size");

    private final SpeakingSessions sessions;
    private final SpeakingRealtime realtime;
    private final SpeakingHistory speakingHistory;

    @GetMapping("/availability")
    public ApiResponse<Availability> availability(@RequestParam(required = false) SpeakingScopeType scopeType,
                                                  @RequestParam(required = false) String topicId,
                                                  @RequestParam(required = false) String topicItemId) {
        return ApiResponse.ok(sessions.availability(new SpeakingScopeDto(scopeType, topicId, topicItemId)));
    }

    @PostMapping("/sessions")
    public ApiResponse<Session> create(@Valid @RequestBody Create request, HttpServletResponse response) {
        var result = sessions.create(request);
        beginOpening(result.session());
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.session());
    }

    @PostMapping("/sessions/replace")
    public ApiResponse<?> replace(@Valid @RequestBody Replace request, HttpServletResponse response) {
        var result = sessions.replace(request);
        realtime.sessionChanged(request.sessionId());
        if (result.replacingSessionId() != null) {
            response.setStatus(202);
            return ApiResponse.ok(new Replacing("REPLACING", result.replacingSessionId()));
        }
        beginOpening(result.session());
        response.setStatus(result.created() ? 201 : 200);
        return ApiResponse.ok(result.session());
    }

    private void beginOpening(Session session) {
        if (session.status() == SpeakingSessionStatus.INITIALIZING) realtime.openSession(session.sessionId());
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
        var session = sessions.end(id, request.controllerEpoch());
        realtime.sessionChanged(id);
        return ApiResponse.ok(session);
    }

    @GetMapping("/sessions")
    public ApiResponse<Page<Session>> history(@RequestParam(required = false) String scopeType,
                                              @RequestParam(required = false) String topicId,
                                              @RequestParam(required = false) String topicItemId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam MultiValueMap<String, String> query) {
        if (query.containsKey("group") || query.containsKey("parentTopicId")
                || duplicate(query, "page") || duplicate(query, "size")) {
            throw invalidHistoryQuery();
        }
        if (SCOPE_FILTERS.stream().anyMatch(field -> duplicate(query, field))) throw invalidScope();
        SpeakingScopeDto scope = scopeType == null && topicId == null && topicItemId == null
                ? null : new SpeakingScopeDto(historyScopeType(scopeType), topicId, topicItemId);
        return ApiResponse.ok(sessions.history(scope, page, size));
    }

    private static SpeakingScopeType historyScopeType(String value) {
        if (value == null) return null;
        try {
            return SpeakingScopeType.valueOf(value.strip());
        } catch (IllegalArgumentException error) {
            throw invalidScope();
        }
    }

    @GetMapping("/history/topics")
    public ApiResponse<Groups> historyTopics(@RequestParam(required = false) Group group,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam MultiValueMap<String, String> query) {
        validateHistoryQuery(query, GROUP_PARAMETERS);
        return ApiResponse.ok(speakingHistory.topics(group, page, size));
    }

    @GetMapping("/history/topics/{topicId}/sessions")
    public ApiResponse<ParentSessions> historyParent(@PathVariable String topicId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam MultiValueMap<String, String> query) {
        validateHistoryQuery(query, PARENT_PARAMETERS);
        return ApiResponse.ok(speakingHistory.parent(topicId, page, size));
    }

    private static void validateHistoryQuery(MultiValueMap<String, String> query, Set<String> allowed) {
        if (query.entrySet().stream().anyMatch(entry -> !allowed.contains(entry.getKey())
                || entry.getValue().size() != 1 || entry.getValue().getFirst().isBlank())) {
            throw invalidHistoryQuery();
        }
    }

    private static boolean duplicate(MultiValueMap<String, String> query, String field) {
        return query.containsKey(field) && query.get(field).size() > 1;
    }

    private static SpeakingException invalidHistoryQuery() {
        return new SpeakingException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid Speaking history query", false);
    }

    private static SpeakingException invalidScope() {
        return new SpeakingException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE", "Invalid Speaking scope", false);
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
