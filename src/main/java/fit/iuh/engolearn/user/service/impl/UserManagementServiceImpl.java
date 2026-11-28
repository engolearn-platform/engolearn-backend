package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.user.dto.request.ManagementRequests.*;
import fit.iuh.engolearn.user.dto.response.ManagementResponses.*;
import com.mongodb.MongoException;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.user.exception.UserManagementFailure;
import fit.iuh.engolearn.user.model.*;
import fit.iuh.engolearn.user.model.enums.*;
import fit.iuh.engolearn.user.repository.UserManagementRepository;
import fit.iuh.engolearn.user.service.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import static fit.iuh.engolearn.user.exception.UserManagementFailure.Reason.*;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementServiceImpl implements UserManagementService {
    private final UserManagementRepository users;
    private final UserRegistrationService registration;
    private final PasswordEncoder encoder;
    private final CurrentUserProvider caller;
    private final ObjectProvider<MongoTransactionManager> transactions;

    public ListResponse list(ListRequest q) {
        if (q.search() == null || q.search().length() > 100 || q.status() == ManagementStatus.DELETED
                || !Set.of("createdAt,asc","createdAt,desc","name,asc","name,desc").contains(q.sort())
                || q.size() < 1 || q.size() > 100 || q.page() < 0 || q.page() > 100000 / q.size()) throw invalid();
        var page = users.list(new UserRoster.Filter(q.search(), q.role(), q.status(), q.sort(), q.page(), q.size()));
        var items = page.items().stream().map(row -> new SummaryResponse(row.id(), row.name(), row.email(), row.role(),
                row.status(), row.createdAt())).toList();
        return new ListResponse(items, q.page(), q.size(), page.total(), (page.total() + q.size() - 1) / q.size());
    }

    public DetailResponse detail(String id) {
        var row = load(id);
        return new DetailResponse(row.id(), row.name(), row.email(), row.role(), row.status(), row.createdAt(), row.revision());
    }

    public DetailResponse create(CreateRequest request) {
        String actor = caller.requireCurrentUserId();
        manageableRole(request.role());
        String name = name(request.name());
        String email;
        try {
            email = AccountCredentials.canonicalEmail(request.email());
            AccountCredentials.validatePassword(request.initialPassword());
        } catch (AuthFailure failure) {
            throw invalid();
        }
        var user = User.builder().email(email).passwordHash(encoder.encode(request.initialPassword())).role(request.role()).build();
        try {
            return RegistrationTransactionRetry.run(() -> transaction(() -> {
                User saved;
                try { saved = registration.register(user); }
                catch (DuplicateKeyException duplicate) { throw new UserManagementFailure(EMAIL_ALREADY_EXISTS); }
                var now = Instant.now();
                if (!users.rename(saved.getId(), null, name, now)) throw conflict();
                audit(actor, saved.getId(), ManagementAction.CREATE, null, request.role(), ManagementStatus.ACTIVE, now);
                return detail(saved.getId());
            }));
        } catch (AuthFailure unavailable) {
            throw new UserManagementFailure(SERVICE_UNAVAILABLE);
        }
    }

    public DetailResponse edit(String id, EditRequest request) {
        return change(id, request.expectedRevision(), ManagementAction.EDIT_NAME, null, request.expectedName(), name(request.name()));
    }

    public DetailResponse role(String id, RoleRequest request) {
        manageableRole(request.role());
        return change(id, request.expectedRevision(), ManagementAction.CHANGE_ROLE, request.role(), null, null);
    }

    public DetailResponse status(String id, StatusRequest request) {
        if (request.status() == null || request.status() == ManagementStatus.DELETED) throw invalid();
        return change(id, request.expectedRevision(), request.status() == ManagementStatus.ACTIVE
                ? ManagementAction.ENABLE : ManagementAction.DISABLE, null, null, null);
    }

    public DeletedResponse delete(String id, long revision) {
        change(id, revision, ManagementAction.DELETE, null, null, null);
        return new DeletedResponse(id);
    }

    private DetailResponse change(String id, long revision, ManagementAction action, UserRole newRole,
                                  String expectedName, String newName) {
        if (revision < 0) throw invalid();
        String actor = caller.requireCurrentUserId();
        try {
            return transaction(() -> {
                var before = mutable(id);
                if (before.revision() != revision) throw conflict();
                var nextStatus = switch (action) {
                    case DELETE -> ManagementStatus.DELETED;
                    case ENABLE -> ManagementStatus.ACTIVE;
                    case DISABLE -> ManagementStatus.DISABLED;
                    default -> before.status();
                };
                var nextRole = newRole == null ? before.role() : newRole;
                if (action == ManagementAction.CHANGE_ROLE && nextRole == before.role()
                        || (action == ManagementAction.ENABLE || action == ManagementAction.DISABLE) && nextStatus == before.status()
                        || action == ManagementAction.EDIT_NAME && !Objects.equals(expectedName, before.name())) throw conflict();
                var now = Instant.now();
                if (!users.update(before, action, nextRole, now)) throw conflict();
                if (action == ManagementAction.EDIT_NAME && !users.rename(id, expectedName, newName, now)) throw conflict();
                audit(actor, id, action, before, nextRole, nextStatus, now);
                return action == ManagementAction.DELETE ? null : detail(id);
            });
        } catch (RuntimeException failure) {
            if (!writeConflict(failure)) throw failure;
            mutable(id); // Classify tombstones and newly protected targets after the transaction aborts.
            throw conflict();
        }
    }

    private static boolean writeConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && mongo.getCode() == 112) return true;
        }
        return false;
    }

    private <T> T transaction(Supplier<T> work) {
        var manager = transactions.getIfAvailable();
        if (manager == null) throw new UserManagementFailure(SERVICE_UNAVAILABLE);
        return new TransactionTemplate(manager).execute(tx -> work.get());
    }

    private UserRoster.Entry load(String id) {
        return users.find(id).orElseThrow(() -> new UserManagementFailure(USER_NOT_FOUND));
    }

    private UserRoster.Entry mutable(String id) {
        var before = load(id);
        if (before.role() == UserRole.ADMIN) throw new UserManagementFailure(ADMIN_TARGET_READ_ONLY);
        return before;
    }

    private void audit(String actor, String id, ManagementAction action, UserRoster.Entry before,
                       UserRole role, ManagementStatus status, Instant now) {
        users.append(UserManagementAudit.builder().actorId(actor).targetId(id).action(action).createdAt(now)
                .beforeRole(before == null ? null : before.role()).beforeStatus(before == null ? null : before.status())
                .afterRole(role).afterStatus(status).build());
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 80) throw invalid();
        return value.strip();
    }
    private static void manageableRole(UserRole role) {
        if (role != UserRole.USER && role != UserRole.CONTENT_MANAGER) throw invalid();
    }
    private static UserManagementFailure invalid() { return new UserManagementFailure(VALIDATION_ERROR); }
    private static UserManagementFailure conflict() { return new UserManagementFailure(USER_STATE_CONFLICT); }
}
