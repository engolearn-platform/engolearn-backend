package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.speaking.dto.request.CreateSpeakingSessionRequest;
import fit.iuh.engolearn.speaking.dto.request.SpeakingControlRequest;
import fit.iuh.engolearn.speaking.dto.response.*;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAsset;
import fit.iuh.engolearn.speaking.service.SpeakingSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/speaking")
public class SpeakingController {
    private final SpeakingSessionService service;

    public SpeakingController(SpeakingSessionService service) { this.service = service; }

    @GetMapping("/items/{itemId}/config")
    public ApiResponse<SpeakingItemConfigResponse> config(@PathVariable @NotBlank String itemId) {
        return ApiResponse.ok(service.config(itemId));
    }

    @GetMapping("/sessions/current")
    public ApiResponse<SpeakingSessionResponse> current() { return ApiResponse.ok(service.current()); }

    @GetMapping("/sessions")
    public ApiResponse<List<SpeakingSessionResponse>> history(
            @RequestParam(required = false) String itemId) {
        return ApiResponse.ok(service.history(itemId));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<SpeakingSessionResponse> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/sessions")
    public ApiResponse<SpeakingSessionResponse> create(@Valid @RequestBody CreateSpeakingSessionRequest body) {
        return ApiResponse.ok(service.create(body.itemId(), body.requestId(), false));
    }

    @PostMapping("/sessions/replace")
    public ApiResponse<SpeakingSessionResponse> replace(@Valid @RequestBody CreateSpeakingSessionRequest body) {
        return ApiResponse.ok(service.create(body.itemId(), body.requestId(), true));
    }

    @PostMapping("/sessions/{id}/resume")
    public ApiResponse<SpeakingSessionResponse> resume(@PathVariable String id,
            @Valid @RequestBody SpeakingControlRequest body) {
        return ApiResponse.ok(service.resume(id, body.requestId()));
    }

    @PostMapping("/sessions/{id}/end")
    public ApiResponse<SpeakingSessionResponse> end(@PathVariable String id,
            @Valid @RequestBody SpeakingControlRequest body) {
        return ApiResponse.ok(service.end(id, body.controllerEpoch(), body.requestId()));
    }

    @PostMapping("/sessions/{id}/socket-ticket")
    public ApiResponse<SpeakingSocketTicketResponse> socketTicket(@PathVariable String id,
            @Valid @RequestBody SpeakingControlRequest body) {
        return ApiResponse.ok(service.ticket(id, body.controllerEpoch()));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<SpeakingSessionResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(service.delete(id));
    }

    @DeleteMapping("/sessions")
    public ApiResponse<Integer> deleteAll() { return ApiResponse.ok(service.deleteAll()); }

    @GetMapping("/sessions/{id}/turns/{turnId}/audio/{kind}")
    public ApiResponse<SpeakingAudioResponse> playback(@PathVariable String id,
            @PathVariable String turnId, @PathVariable SpeakingAudioAsset.Kind kind) {
        return ApiResponse.ok(service.playback(id, turnId, kind));
    }
}
