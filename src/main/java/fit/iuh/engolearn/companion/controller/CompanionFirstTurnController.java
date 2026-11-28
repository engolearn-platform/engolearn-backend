package fit.iuh.engolearn.companion.controller;

import fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companion/first-turns")
public class CompanionFirstTurnController {
    private final CompanionTurnService turns;

    @GetMapping("/{turnId}")
    public ApiResponse<FirstTurnStatusResponse> get(@PathVariable UUID turnId) {
        return ApiResponse.ok(turns.getFirstTurn(turnId));
    }

    @PostMapping("/{turnId}/cancel")
    public ApiResponse<FirstTurnStatusResponse> cancel(@PathVariable UUID turnId) {
        return ApiResponse.ok(turns.cancelFirstTurn(turnId));
    }

    @ModelAttribute
    void noCache(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
