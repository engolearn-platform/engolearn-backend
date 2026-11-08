package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.placement.dto.request.SavePlacementAnswerRequest;
import fit.iuh.engolearn.placement.dto.response.*;
import fit.iuh.engolearn.placement.service.PlacementAccessContext;
import fit.iuh.engolearn.placement.service.PlacementAttemptService;
import fit.iuh.engolearn.placement.service.PlacementClaimService;
import fit.iuh.engolearn.placement.service.PlacementStartOutcome;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/placement/attempts")
public class PlacementAttemptController {
    private static final String GUEST_COOKIE = "placement_guest_session";
    private final PlacementAttemptService service;
    private final PlacementClaimService claimService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<StartPlacementAttemptResponse> start(
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        String userId = currentUserProvider.getCurrentUser().map(user -> user.id()).orElse(null);
        if (userId == null && !service.isKnownGuestSession(guestSessionId)) {
            guestSessionId = UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(GUEST_COOKIE, guestSessionId)
                    .httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                    .path("/api/v1/placement").build().toString());
        }
        PlacementStartOutcome outcome = service.start(new PlacementAccessContext(userId, guestSessionId));
        response.setStatus(outcome.created() ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
        return ApiResponse.ok(outcome.response());
    }

    @GetMapping("/current")
    public ApiResponse<PlacementCurrentAttemptResponse> current(
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.current(access(guestSessionId)));
    }

    @GetMapping("/{attemptId}")
    public ApiResponse<PlacementAttemptResponse> get(
            @PathVariable @NotBlank String attemptId,
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.get(attemptId, access(guestSessionId)));
    }

    @PatchMapping("/{attemptId}/answers")
    public ApiResponse<SavePlacementAnswerResponse> saveAnswer(
            @PathVariable @NotBlank String attemptId, @Valid @RequestBody SavePlacementAnswerRequest body,
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.saveAnswer(attemptId, body.questionId(), body.selectedOptionId(),
                access(guestSessionId)));
    }

    @PostMapping("/{attemptId}/submit")
    public ApiResponse<PlacementSubmitResponse> submit(
            @PathVariable @NotBlank String attemptId,
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(service.submit(attemptId, access(guestSessionId)));
    }

    @GetMapping("/{attemptId}/claim-gate")
    public ApiResponse<PlacementClaimGateResponse> claimGate(
            @PathVariable @NotBlank String attemptId,
            @CookieValue(value = GUEST_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(claimService.getClaimGate(attemptId, access(guestSessionId)));
    }

    private PlacementAccessContext access(String guestSessionId) {
        return new PlacementAccessContext(currentUserProvider.getCurrentUser().map(user -> user.id()).orElse(null),
                guestSessionId);
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
