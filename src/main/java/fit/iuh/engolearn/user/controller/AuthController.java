package fit.iuh.engolearn.user.controller;

import fit.iuh.engolearn.shared.security.CurrentUser;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.user.dto.response.AuthUserResponse;
import fit.iuh.engolearn.user.dto.request.LoginRequest;
import fit.iuh.engolearn.user.dto.request.RegisterRequest;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.service.UserAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CurrentUserProvider currentUserProvider;
    private final UserAuthService authService;
    public AuthController(CurrentUserProvider currentUserProvider, UserAuthService authService) {
        this.currentUserProvider = currentUserProvider; this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return ApiResponse.ok(new AuthUserResponse(currentUser.id(), currentUser.email(), currentUser.role()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthUserResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletResponse response) {
        User user = authService.registerAndIssue(body.email(), body.password(), response);
        return ApiResponse.ok(view(user));
    }

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        User user = authService.loginAndIssue(body.email(), body.password(), response);
        return ApiResponse.ok(view(user));
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(@CookieValue(name = "REFRESH_TOKEN", required = false) String oldToken, HttpServletResponse response) {
        authService.refresh(oldToken, response);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CookieValue(name = "ACCESS_TOKEN", required = false) String access,
                                    @CookieValue(name = "REFRESH_TOKEN", required = false) String refresh, HttpServletResponse response) {
        authService.logout(access, refresh, response);
        return ApiResponse.ok(null);
    }
    private AuthUserResponse view(User user) { return new AuthUserResponse(user.getId(), user.getEmail(), user.getRole()); }
}
