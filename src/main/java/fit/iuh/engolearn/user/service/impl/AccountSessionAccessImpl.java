package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.user.contract.AccountSessionAccess;
import fit.iuh.engolearn.user.contract.dto.AccountSessionProofDto;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AccountSessionAccessImpl implements AccountSessionAccess {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;

    public Optional<AccountSessionProofDto> current(String ownerId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)
                || !principal.getId().equals(ownerId) || principal.getSessionId() == null) return Optional.empty();
        var proof = new AccountSessionProofDto(ownerId, principal.getSessionId(), principal.getAccountSessionEpoch());
        return active(proof) ? Optional.of(proof) : Optional.empty();
    }

    public boolean active(AccountSessionProofDto proof) {
        if (proof == null || proof.accountId() == null || proof.sessionId() == null) return false;
        try {
            var session = sessions.findBySessionId(proof.sessionId()).orElse(null);
            return session != null && proof.accountId().equals(session.getAccountId()) && !session.isRevoked()
                    && session.getExpiresAt() != null && session.getExpiresAt().isAfter(Instant.now())
                    && session.getAccountSessionEpoch() == proof.accountSessionEpoch()
                    && users.findById(proof.accountId()).filter(user -> user.active()
                        && user.getSessionEpoch() == proof.accountSessionEpoch()).isPresent();
        } catch (DataAccessException unavailable) {
            return false;
        }
    }
}
