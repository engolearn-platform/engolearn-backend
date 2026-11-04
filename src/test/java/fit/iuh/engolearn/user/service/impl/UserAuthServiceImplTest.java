package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.UserRepository;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.service.UserRegistrationService;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import fit.iuh.engolearn.user.model.RefreshSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAuthServiceImplTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final UserRegistrationService registrations = mock(UserRegistrationService.class);
    private final UserAuthServiceImpl service = new UserAuthServiceImpl(repository, encoder,
            sessions, registrations, tokens, 900);

    @Test
    void registerCanonicalizesEmailHashesPasswordAndAssignsUserRole() {
        when(encoder.encode("a safe password")).thenReturn("encoded");
        when(registrations.register(any())).thenAnswer(call -> call.getArgument(0));

        User account = service.register(" Student@Example.COM ", "a safe password");

        assertThat(account.getEmail()).isEqualTo("student@example.com");
        assertThat(account.getPasswordHash()).isEqualTo("encoded");
        assertThat(account.getRole().name()).isEqualTo("USER");
    }

    @Test
    void lockedAccountCannotLogIn() {
        User account = new User("id", "student@example.com", "encoded", null, true);
        when(repository.findByEmail("student@example.com")).thenReturn(Optional.of(account));
        when(encoder.matches("a safe password", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> service.login("Student@Example.COM", "a safe password"))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("ACCOUNT_LOCKED"));
    }

    @Test
    void wrongPasswordForLockedAccountRemainsInvalidCredentials() {
        User account = new User("id", "student@example.com", "encoded", null, true);
        when(repository.findByEmail("student@example.com")).thenReturn(Optional.of(account));
        when(encoder.matches("wrong password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.login("student@example.com", "wrong password"))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsPasswordsWhoseUtf8EncodingExceedsBcryptLimit() {
        String password = "é".repeat(37);
        assertThatThrownBy(() -> service.register("student@example.com", password))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("VALIDATION_ERROR"));
        verifyNoInteractions(encoder);
    }

    @Test
    void loginRejectsPasswordsWhoseUtf8EncodingExceedsBcryptLimitBeforeMatching() {
        User account = new User("id", "student@example.com", "encoded", null, false);
        when(repository.findByEmail("student@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login("student@example.com", "é".repeat(37)))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(failure.getStatus()).isEqualTo(401);
                });
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void rejectsEmailLongerThan254CharactersOnLoginAndRegistration() {
        String email = "a".repeat(250) + "@b.com";

        assertThatThrownBy(() -> service.register(email, "a safe password"))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.login(email, "a safe password"))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(400));
        verifyNoInteractions(repository, encoder);
    }

    @Test
    void loginRejectsMalformedEmailAsValidationFailure() {
        assertThatThrownBy(() -> service.login(" ", "password"))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(400));
        verifyNoInteractions(repository);
    }

    @Test
    void refreshKeepsAbsoluteSessionExpiryAndDoesNotClearCookiesWhenCasLosesRace() {
        JwtTokenService tokens = mock(JwtTokenService.class);
        RefreshSessionRepository sessionRepository = mock(RefreshSessionRepository.class);
        User account = new User("account", "student@example.com", "encoded", null, false);
        UserAuthServiceImpl auth = new UserAuthServiceImpl(repository, encoder, sessionRepository, registrations, tokens, 900);
        when(tokens.verify("old", "refresh")).thenReturn(new JwtTokenService.Claims("account", "session", "refresh"));
        when(tokens.hash("old")).thenReturn("old-hash");
        when(tokens.create(eq("account"), eq("session"), eq("refresh"), anyLong())).thenReturn("next");
        when(sessionRepository.findBySessionId("session")).thenReturn(Optional.of(RefreshSession.builder().sessionId("session").accountId("account").tokenHash("old-hash").expiresAt(java.time.Instant.now().plusSeconds(600)).build()));
        when(repository.findById("account")).thenReturn(Optional.of(new User("account", "student@example.com", "encoded", null, false)));
        when(sessionRepository.rotateIfCurrent(eq("session"), eq("account"), eq("old-hash"), eq("next-hash"), any())).thenReturn(false);
        when(tokens.hash("next")).thenReturn("next-hash");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> auth.refresh("old", response))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(401));
        verify(response, never()).addHeader(eq("Set-Cookie"), any());
        verify(sessionRepository).rotateIfCurrent(eq("session"), eq("account"), eq("old-hash"), eq("next-hash"), any());
    }

    @Test
    void refreshPropagatesPersistenceFailures() {
        JwtTokenService tokens = mock(JwtTokenService.class);
        RefreshSessionRepository sessionRepository = mock(RefreshSessionRepository.class);
        UserAuthServiceImpl auth = new UserAuthServiceImpl(repository, encoder, sessionRepository, registrations, tokens, 900);
        when(tokens.verify("old", "refresh")).thenReturn(new JwtTokenService.Claims("account", "session", "refresh"));
        when(tokens.hash("old")).thenReturn("old-hash");
        when(tokens.create(eq("account"), eq("session"), eq("refresh"), anyLong())).thenReturn("next");
        when(tokens.hash("next")).thenReturn("next-hash");
        when(sessionRepository.findBySessionId("session")).thenReturn(Optional.of(RefreshSession.builder().sessionId("session").accountId("account").expiresAt(java.time.Instant.now().plusSeconds(600)).build()));
        when(repository.findById("account")).thenReturn(Optional.of(new User("account", "student@example.com", "encoded", null, false)));
        when(sessionRepository.rotateIfCurrent(any(), any(), any(), any(), any())).thenThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatThrownBy(() -> auth.refresh("old", mock(HttpServletResponse.class)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void lockedAccountCannotRotateRefreshSession() {
        JwtTokenService tokens = mock(JwtTokenService.class);
        RefreshSessionRepository sessionRepository = mock(RefreshSessionRepository.class);
        UserAuthServiceImpl auth = new UserAuthServiceImpl(repository, encoder, sessionRepository, registrations, tokens, 900);
        when(tokens.verify("old", "refresh")).thenReturn(new JwtTokenService.Claims("account", "session", "refresh"));
        when(sessionRepository.findBySessionId("session")).thenReturn(Optional.of(RefreshSession.builder().sessionId("session").accountId("account").expiresAt(java.time.Instant.now().plusSeconds(600)).build()));
        when(repository.findById("account")).thenReturn(Optional.of(new User("account", "student@example.com", "encoded", null, true)));

        assertThatThrownBy(() -> auth.refresh("old", mock(HttpServletResponse.class)))
                .isInstanceOfSatisfying(AuthFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(403));
        verify(sessionRepository, never()).rotateIfCurrent(any(), any(), any(), any(), any());
    }

    @Test
    void logoutUsesValidRefreshWhenAccessTokenIsExpired() {
        when(tokens.verify("expired-access", "access")).thenThrow(new IllegalArgumentException("expired"));
        when(tokens.verify("valid-refresh", "refresh")).thenReturn(new JwtTokenService.Claims("account", "session", "refresh"));

        service.logout("expired-access", "valid-refresh", mock(HttpServletResponse.class));

        verify(sessions).revokeIfOwnedBy("session", "account");
    }

    @Test
    void logoutPropagatesPersistenceFailures() {
        when(tokens.verify("valid-access", "access")).thenReturn(new JwtTokenService.Claims("account", "session", "access"));
        when(sessions.revokeIfOwnedBy("session", "account")).thenThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatThrownBy(() -> service.logout("valid-access", null, mock(HttpServletResponse.class)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
