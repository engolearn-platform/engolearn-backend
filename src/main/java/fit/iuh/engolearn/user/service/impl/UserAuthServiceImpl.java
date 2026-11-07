package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.repository.UserRepository;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.service.UserRegistrationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserAuthServiceImpl implements fit.iuh.engolearn.user.service.UserAuthService {
    private static final long SESSION_LIFETIME_SECONDS = 604800;
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final RefreshSessionRepository sessions;
    private final UserRegistrationService registrations;
    private final JwtTokenService tokens;
    private final long accessTokenSeconds;
    @Value("${engolearn.security.cookies.secure:true}") private boolean secureCookies;

    public UserAuthServiceImpl(UserRepository repository, PasswordEncoder encoder, RefreshSessionRepository sessions,
                              UserRegistrationService registrations,
                              JwtTokenService tokens,
                              @Value("${engolearn.security.access-token-seconds:900}") long accessTokenSeconds) {
        if (accessTokenSeconds <= 0) throw new IllegalArgumentException("Access token lifetime must be positive");
        this.repository = repository;
        this.encoder = encoder;
        this.sessions = sessions;
        this.registrations = registrations;
        this.tokens = tokens;
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public User register(String email, String password) {
        String canonical = canonicalEmail(email);
        validatePassword(password);
        User account = User.builder().email(canonical).passwordHash(encoder.encode(password)).role(UserRole.USER).locked(false).build();
        try { return registrations.register(account); }
        catch (DuplicateKeyException exception) { throw new AuthFailure("Email already exists", "EMAIL_ALREADY_EXISTS", 409); }
    }

    public User login(String email, String password) {
        String canonical = canonicalEmail(email);
        if (password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new AuthFailure("Invalid credentials", "INVALID_CREDENTIALS", 401);
        User account = repository.findByEmail(canonical).orElseThrow(() -> new AuthFailure("Invalid credentials", "INVALID_CREDENTIALS", 401));
        if (!encoder.matches(password, account.getPasswordHash())) throw new AuthFailure("Invalid credentials", "INVALID_CREDENTIALS", 401);
        if (account.isLocked()) throw new AuthFailure("Account is locked", "ACCOUNT_LOCKED", 403);
        return account;
    }

    public User loginById(String accountId) {
        User account = repository.findById(accountId).orElseThrow(() -> new AuthFailure("Refresh token is invalid", "REFRESH_TOKEN_INVALID", 401));
        if (account.isLocked()) throw new AuthFailure("Account is locked", "ACCOUNT_LOCKED", 403);
        return account;
    }

    public User registerAndIssue(String email, String password, HttpServletResponse response) { User account = register(email, password); issue(account, response); return account; }
    public User loginAndIssue(String email, String password, HttpServletResponse response) { User account = login(email, password); issue(account, response); return account; }

    public void refresh(String oldToken, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        JwtTokenService.Claims claims;
        try { claims = tokens.verify(oldToken, "refresh"); }
        catch (IllegalArgumentException exception) { throw invalidRefresh(); }

        Instant now = Instant.now();
        RefreshSession session = sessions.findBySessionId(claims.sessionId()).orElseThrow(UserAuthServiceImpl::invalidRefresh);
        if (!claims.accountId().equals(session.getAccountId()) || session.isRevoked() || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now))
            throw invalidRefresh();
        User account = loginById(claims.accountId());
        long remainingSeconds = session.getExpiresAt().getEpochSecond() - now.getEpochSecond();
        if (remainingSeconds <= 0) throw invalidRefresh();
        String next = tokens.create(account.getId(), claims.sessionId(), "refresh", remainingSeconds);
        if (!sessions.rotateIfCurrent(claims.sessionId(), claims.accountId(), tokens.hash(oldToken), tokens.hash(next), now))
            throw invalidRefresh();
        response.addHeader("Set-Cookie", cookie("ACCESS_TOKEN", tokens.create(account.getId(), claims.sessionId(), "access", accessTokenSeconds), "/api", accessTokenSeconds).toString());
        response.addHeader("Set-Cookie", cookie("REFRESH_TOKEN", next, "/api/v1/auth", remainingSeconds).toString());
    }

    public void logout(String access, String refresh, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        JwtTokenService.Claims claims = verifyForLogout(access, "access");
        if (claims == null) claims = verifyForLogout(refresh, "refresh");
        if (claims != null) sessions.revokeIfOwnedBy(claims.sessionId(), claims.accountId());
        clear(response);
    }

    private JwtTokenService.Claims verifyForLogout(String token, String type) {
        if (token == null || token.isBlank()) return null;
        try { return tokens.verify(token, type); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void issue(User account, HttpServletResponse response) {
        String sid = UUID.randomUUID().toString();
        String refresh = tokens.create(account.getId(), sid, "refresh", SESSION_LIFETIME_SECONDS);
        sessions.save(RefreshSession.builder().sessionId(sid).accountId(account.getId()).tokenHash(tokens.hash(refresh))
                .expiresAt(Instant.now().plusSeconds(SESSION_LIFETIME_SECONDS)).revoked(false).generation(0).build());
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", cookie("ACCESS_TOKEN", tokens.create(account.getId(), sid, "access", accessTokenSeconds), "/api", accessTokenSeconds).toString());
        response.addHeader("Set-Cookie", cookie("REFRESH_TOKEN", refresh, "/api/v1/auth", SESSION_LIFETIME_SECONDS).toString());
    }

    private ResponseCookie cookie(String name, String value, String path, long age) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secureCookies).sameSite("Lax").path(path).maxAge(age).build();
    }
    private void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie("ACCESS_TOKEN", "", "/api", 0).toString());
        response.addHeader("Set-Cookie", cookie("REFRESH_TOKEN", "", "/api/v1/auth", 0).toString());
    }
    private static AuthFailure invalidRefresh() { return new AuthFailure("Refresh token is invalid", "REFRESH_TOKEN_INVALID", 401); }
    private static void validatePassword(String password) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < 8 || bytes > 72)
            throw new AuthFailure("Password must contain at least 8 characters and fit the supported password limit", "VALIDATION_ERROR", 400);
    }
    private String canonicalEmail(String email) {
        if (email == null || email.trim().length() > 254 || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new AuthFailure("A valid email is required", "VALIDATION_ERROR", 400);
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
