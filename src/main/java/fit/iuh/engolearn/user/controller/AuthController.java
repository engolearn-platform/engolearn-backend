package fit.iuh.engolearn.user.controller;

import fit.iuh.engolearn.shared.security.CurrentUser;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.user.dto.response.AuthUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CurrentUserProvider currentUserProvider;

    public AuthController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me() {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return ApiResponse.ok(new AuthUserResponse(currentUser.id(), currentUser.email(), currentUser.role()));
    }
}
