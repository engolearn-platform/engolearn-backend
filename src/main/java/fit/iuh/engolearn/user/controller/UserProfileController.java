package fit.iuh.engolearn.user.controller;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.user.dto.response.UserProfileResponse;
import fit.iuh.engolearn.user.dto.request.UpdateDisplayNameRequest;
import fit.iuh.engolearn.user.service.UserProfileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/profile")
public class UserProfileController {
    private final CurrentUserProvider users;
    private final UserProfileService profiles;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(profiles.getCurrentProfile(users.requireCurrentUserId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> update(@RequestBody UpdateDisplayNameRequest body,
                                                   HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(profiles.updateDisplayName(users.requireCurrentUserId(), body.displayName()));
    }
}
