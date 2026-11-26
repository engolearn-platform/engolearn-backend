package fit.iuh.engolearn.companion.controller;

import fit.iuh.engolearn.companion.dto.request.UpdateCompanionMemoryRequest;
import fit.iuh.engolearn.companion.dto.request.UpdateCompanionMemorySettingsRequest;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryDeletedResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryPageResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsUpdateResponse;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companion")
public class CompanionMemoryController {
    private final CompanionMemoryService memories;
    private final CurrentUserProvider currentUser;

    @GetMapping("/memory")
    public ApiResponse<CompanionMemoryPageResponse> list(@RequestParam(required = false) MemoryCategory category,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(memories.list(currentUser.requireCurrentUserId(), category, before, limit));
    }

    @PatchMapping("/memory/{memoryId}")
    public ApiResponse<CompanionMemoryResponse> update(@PathVariable String memoryId,
                                                        @Valid @RequestBody UpdateCompanionMemoryRequest request) {
        return ApiResponse.ok(memories.update(currentUser.requireCurrentUserId(), memoryId,
                request.content(), request.expectedVersion()));
    }

    @DeleteMapping("/memory/{memoryId}")
    public ApiResponse<CompanionMemoryDeletedResponse> forget(@PathVariable String memoryId,
                                                               @RequestParam @Positive long expectedVersion) {
        String learnerId = currentUser.requireCurrentUserId();
        memories.forget(learnerId, memoryId, expectedVersion);
        return ApiResponse.ok(new CompanionMemoryDeletedResponse(memoryId, true));
    }

    @GetMapping("/memory/settings")
    public ApiResponse<CompanionMemorySettingsResponse> settings() {
        return ApiResponse.ok(memories.settings(currentUser.requireCurrentUserId()));
    }

    @PutMapping("/memory/settings")
    public ApiResponse<CompanionMemorySettingsUpdateResponse> updateSettings(@Valid @RequestBody UpdateCompanionMemorySettingsRequest request) {
        return ApiResponse.ok(memories.updateSettings(currentUser.requireCurrentUserId(), request.automaticMemoryEnabled()));
    }

    @org.springframework.web.bind.annotation.ModelAttribute
    void noCache(HttpServletResponse response) { response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); }
}
