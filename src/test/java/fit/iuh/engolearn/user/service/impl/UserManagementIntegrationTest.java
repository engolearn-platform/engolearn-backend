package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.*;
import fit.iuh.engolearn.shared.security.impl.*;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.dto.response.CurrentBenefitsResponse;
import fit.iuh.engolearn.subscription.model.SubscriptionAccount;
import fit.iuh.engolearn.subscription.service.SubscriptionPublicationService;
import fit.iuh.engolearn.user.dto.request.ManagementRequests.*;
import fit.iuh.engolearn.user.exception.UserManagementFailure;
import fit.iuh.engolearn.user.infra.UserAuthIndexInitializer;
import fit.iuh.engolearn.user.model.*;
import fit.iuh.engolearn.user.model.enums.*;
import fit.iuh.engolearn.user.service.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfDockerAvailable
@AutoConfigureMockMvc
@TestPropertySource(properties="engolearn.security.jwt.secret=user-management-test-only-secret-at-least-32-bytes")
class UserManagementIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String PATH = "/api/v1/admin/users";
    private static final String EMAIL = "learner@example.test";
    private static final String PASSWORD = "fixture-password";
    @Autowired UserManagementService management;
    @Autowired UserAuthService auth;
    @Autowired UserAuthIndexInitializer schema;
    @Autowired SubscriptionPublicationService publication;
    @Autowired JwtTokenService tokens;
    @Autowired MockMvc mvc;

    @BeforeEach
    void schemaAndPolicy() {
        schema.run(null);
        mongoTemplate.createCollection(SubscriptionAccount.class);
        publication.publish(new PublicationManifestRequest("f05", "fixture", List.of(),
                List.of(policy("FREE"), policy("TRIAL")), List.of()));
        account("admin", UserRole.ADMIN);
        admin();
    }

    private PublicationManifestRequest.PolicyRequest policy(String scope) {
        String id = "f05-" + scope;
        return new PublicationManifestRequest.PolicyRequest(id, scope, 1, Instant.parse("2026-01-01T00:00:00Z"),
                new QuotaPolicySnapshotDto(id, scope, 1, List.of(new QuotaRuleDto("TEST","Test",3,"ATTEMPT","MONTHLY",0))),
                new CurrentBenefitsResponse(scope.equals("FREE") ? "FREE" : "PREMIUM", scope, id, List.of()));
    }

    private void account(String id, UserRole role) {
        mongoTemplate.insert(new User(id, id+"@example.test", "fixture", role, false));
        mongoTemplate.insert(RefreshSession.builder().accountId(id).sessionId(id+"-session")
                .expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private Cookie cookie(String id) {
        return new Cookie("ACCESS_TOKEN",tokens.create(id,id+"-session","access",900));
    }

    private static void authorize(String id, UserRole role) {
        var principal = new AuthenticatedUserPrincipal(id, id+"@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    private static void admin() { authorize("admin", UserRole.ADMIN); }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CreateRequest create(UserRole role) { return new CreateRequest("Lan", EMAIL, PASSWORD, role); }
    private static ListRequest all() { return new ListRequest("",null,null,"name,asc",0,100); }

    @Test
    void startupRejectsAmbiguousIdsAndInvalidExplicitCounters() {
        var rows = mongoTemplate.getCollection("users");
        var ambiguous = new Document("_id", "0123456789abcdef01234567").append("email", "import@example.test");
        rows.insertOne(ambiguous);
        assertThatThrownBy(() -> schema.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("_id");
        rows.deleteOne(ambiguous);
        for (var field : List.of("admin_revision", "session_epoch")) {
            var invalid = new Document("_id", "import").append("email", "import@example.test").append(field, null);
            rows.insertOne(invalid);
            assertThatThrownBy(() -> schema.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining(field);
            rows.deleteOne(invalid);
        }
    }

    @Test
    void legacyRosterUsesProfileNamesEscapedSearchStableSortAndBoundedPages() {
        var rows = mongoTemplate.getCollection("users");
        for (String id : List.of("Zed","a","b","c","no-profile")) rows.insertOne(new Document("_id",id)
                .append("email", id.toLowerCase(java.util.Locale.ROOT)+"@example.test"));
        mongoTemplate.insert(UserProfile.builder().id("Zed").displayName("đức").build());
        for (String id : List.of("a","b","c")) mongoTemplate.insert(UserProfile.builder().id(id)
                .displayName(id.equals("a") ? "ĐỨC" : id.equals("b") ? "đức" : "Minh [A]").build());
        assertThat(management.list(all()).items()).extracting("id").containsExactly("Zed","a","b","c","admin","no-profile");
        assertThat(management.list(new ListRequest("[a]",null,null,"createdAt,desc",0,1)).items()).extracting("id").containsExactly("c");
        assertThat(management.list(new ListRequest("",UserRole.USER,ManagementStatus.ACTIVE,"name,desc",5,1)).items()).isEmpty();
        assertThat(management.detail("no-profile").name()).isNull();
        assertThat(management.detail("no-profile").revision()).isZero();
        assertThat(management.edit("no-profile",new EditRequest("New",0,null)).name()).isEqualTo("New");
        assertThat(mongoTemplate.findById("no-profile", UserProfile.class).getCefrLevel()).isEqualTo(CefrLevel.A1);
        assertThatThrownBy(() -> management.list(new ListRequest("",null,null,"name,asc",Integer.MAX_VALUE,100)))
                .isInstanceOf(UserManagementFailure.class);
    }

    @Test
    void credentialAndMethodBoundariesRejectGuestsAndLowerRoles() throws Exception {
        clear();
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        for (var role : List.of(UserRole.USER,UserRole.CONTENT_MANAGER)) {
            account(role.name(), role);
            mvc.perform(get(PATH+"/admin").cookie(cookie(role.name()))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            authorize(role.name(), role);
            assertThatThrownBy(() -> management.list(all())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> management.delete("admin",0)).isInstanceOf(AccessDeniedException.class);
            clear();
        }
        mvc.perform(get(PATH+"/admin").cookie(cookie("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void strictWriteShapeAndQueryFailuresAreSecretFree() throws Exception {
        clear();
        for (String body : List.of(
                "{\"name\":\"X\",\"email\":\"u@example.test\",\"initialPassword\":\"fixture-password\",\"role\":null}",
                "{\"name\":123,\"email\":\"u@example.test\",\"initialPassword\":\"fixture-password\"}",
                "{\"name\":\"X\",\"email\":\"u@example.test\",\"initialPassword\":\"fixture-password\",\"locked\":false}",
                "{\"name\":\"X\",\"email\":\"u@example.test\",\"initialPassword\":\"fixture-password\",\"role\":\"ADMIN\"}")) {
            var response = mvc.perform(post(PATH).cookie(cookie("admin")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain(PASSWORD);
        }
        mvc.perform(get(PATH).param("unknown","x").cookie(cookie("admin"))).andExpect(status().isBadRequest());
        mvc.perform(patch(PATH+"/admin").cookie(cookie("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"expectedRevision\":0}")).andExpect(status().isBadRequest());
        mvc.perform(delete(PATH+"/admin").cookie(cookie("admin"))).andExpect(status().isBadRequest());
        var created = mvc.perform(post(PATH).cookie(cookie("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lan\",\"email\":\"LEARNER@example.test\",\"initialPassword\":\"fixture-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.name").value("Lan")).andReturn().getResponse();
        assertThat(created.getHeaders("Set-Cookie")).isEmpty();
        assertThat(created.getContentAsString()).doesNotContain(PASSWORD,"passwordHash","tokenHash");
    }

    @ParameterizedTest
    @EnumSource(value=UserRole.class,names={"USER","CONTENT_MANAGER"})
    void lifecycleFencesTwoDevicesAndPreservesTombstones(UserRole role) throws Exception {
        var user = management.create(create(role));
        var one = login();
        var two = login();
        for (var device : List.of(one,two)) checkAccess(device,200);
        admin();
        var newRole = role == UserRole.USER ? UserRole.CONTENT_MANAGER : UserRole.USER;
        management.role(user.id(),new RoleRequest(newRole,0));
        for (var device : List.of(one,two)) {
            mvc.perform(get("/api/v1/auth/me").cookie(device.getCookie("ACCESS_TOKEN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value(newRole.name()));
        }
        admin();
        management.status(user.id(),new StatusRequest(ManagementStatus.DISABLED,1));
        assertThatThrownBy(this::login).isInstanceOf(AuthFailure.class);
        management.role(user.id(),new RoleRequest(role,2));
        assertThat(management.detail(user.id()).status()).isEqualTo(ManagementStatus.DISABLED);
        management.status(user.id(),new StatusRequest(ManagementStatus.ACTIVE,3));
        for (var device : List.of(one,two)) {
            checkAccess(device,401);
            assertThatThrownBy(() -> auth.refresh(device.getCookie("REFRESH_TOKEN").getValue(),new MockHttpServletResponse()))
                    .isInstanceOf(AuthFailure.class);
        }
        var fresh = login();
        checkAccess(fresh,200);
        admin();
        long revision = 4;
        if (role == UserRole.CONTENT_MANAGER) {
            management.status(user.id(),new StatusRequest(ManagementStatus.DISABLED,revision++));
        }
        management.delete(user.id(),revision);
        assertThatThrownBy(this::login).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> management.detail(user.id())).isInstanceOf(UserManagementFailure.class);
        assertThatThrownBy(() -> management.create(create(role))).isInstanceOf(UserManagementFailure.class);
        assertThatThrownBy(() -> auth.register(EMAIL.toUpperCase(),PASSWORD)).isInstanceOf(AuthFailure.class);
        checkAccess(fresh,401);
        assertThatThrownBy(() -> auth.refresh(fresh.getCookie("REFRESH_TOKEN").getValue(),new MockHttpServletResponse()))
                .isInstanceOf(AuthFailure.class);
        assertThat(mongoTemplate.findById(user.id(),UserProfile.class)).isNotNull();
        assertThat(mongoTemplate.findById(user.id(),SubscriptionAccount.class)).isNotNull();
        assertThat(mongoTemplate.findById(user.id(),User.class).getDeletedAt()).isNotNull();
        assertThat(mongoTemplate.getCollection("user_management_audit").find(new Document("target_id",user.id())))
                .allSatisfy(audit -> assertThat(audit).containsEntry("actor_id","admin").containsKey("created_at").doesNotContainKeys("password","email"));
    }

    @Test
    void competingWritesCommitOneAuditAndCannotOverwriteAnIndependentProfileEdit() throws Exception {
        var u = management.create(create(UserRole.USER));
        var outcomes = race(() -> management.status(u.id(),new StatusRequest(ManagementStatus.DISABLED,0)),
                () -> management.role(u.id(),new RoleRequest(UserRole.CONTENT_MANAGER,0)));
        assertThat(outcomes).filteredOn(UserManagementFailure.class::isInstance).singleElement()
                .satisfies(error -> assertThat(((UserManagementFailure)error).reason().name()).isEqualTo("USER_STATE_CONFLICT"));
        assertThat(management.detail(u.id()).revision()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("user_management_audit").countDocuments()).isEqualTo(2);
        mongoTemplate.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(u.id())),
                new org.springframework.data.mongodb.core.query.Update().set("display_name","F01"),UserProfile.class);
        assertThatThrownBy(() -> management.edit(u.id(),new EditRequest("overwritten",1,"Lan")))
                .isInstanceOf(UserManagementFailure.class);
        assertThat(management.detail(u.id()).name()).isEqualTo("F01");
        assertThat(mongoTemplate.getCollection("user_management_audit").countDocuments()).isEqualTo(2);
    }

    @RepeatedTest(5)
    void fullCanonicalUniqueIndexArbitratesSelfRegistrationVersusAdminCreate() throws Exception {
        assertThat(mongoTemplate.getCollection("users").listIndexes()).anySatisfy(index ->
                assertThat(index).containsEntry("key",new Document("email",1)).containsEntry("unique",true)
                        .doesNotContainKeys("partialFilterExpression","sparse"));
        var outcomes = race(() -> management.create(create(UserRole.USER)), () -> auth.register(EMAIL.toUpperCase(),PASSWORD));
        var errors = outcomes.stream().filter(RuntimeException.class::isInstance).map(RuntimeException.class::cast).toList();
        assertThat(errors).hasSize(1);
        var error = errors.getFirst();
        if (error instanceof AuthFailure authFailure) assertThat(authFailure.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
        else if (error instanceof UserManagementFailure managementFailure)
            assertThat(managementFailure.reason().name()).isEqualTo("EMAIL_ALREADY_EXISTS");
        else throw error;
        assertThat(mongoTemplate.getCollection("users").countDocuments(new Document("email",EMAIL))).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("user_profiles").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection(mongoTemplate.getCollectionName(SubscriptionAccount.class)).countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("user_management_audit").countDocuments())
                .isEqualTo(outcomes.getFirst() instanceof RuntimeException ? 0 : 1);
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first,second).stream().map(action -> pool.submit(() -> {
                admin();
                try {
                    barrier.await(10,TimeUnit.SECONDS);
                    return action.call();
                } catch (RuntimeException failure) {
                    return failure;
                } finally { clear(); }
            })).toList();
            return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }
    }

    @Test
    void bothAdminTargetsAreImmutableAndFailedAuditRollsBackInitializationAndChanges() {
        account("other-admin",UserRole.ADMIN);
        for (String id : List.of("admin","other-admin")) {
            for (Runnable action : List.<Runnable>of(() -> management.edit(id,new EditRequest("X",0,null)),
                    () -> management.role(id,new RoleRequest(UserRole.USER,0)),
                    () -> management.status(id,new StatusRequest(ManagementStatus.DISABLED,0)),
                    () -> management.delete(id,0))) {
                assertThatThrownBy(action::run).isInstanceOfSatisfying(UserManagementFailure.class,
                        failure -> assertThat(failure.reason().name()).isEqualTo("ADMIN_TARGET_READ_ONLY"));
            }
        }
        var u = management.create(create(UserRole.USER));
        mongoTemplate.executeCommand(new Document("collMod","user_management_audit")
                .append("validator",new Document("never_present",new Document("$exists",true))));
        assertThatThrownBy(() -> management.edit(u.id(),new EditRequest("Changed",0,"Lan")))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(management.detail(u.id()).revision()).isZero();
        assertThat(management.detail(u.id()).name()).isEqualTo("Lan");
        assertThatThrownBy(() -> management.create(new CreateRequest("Other","other@example.test",PASSWORD,UserRole.USER)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(mongoTemplate.getCollection("users").countDocuments(new Document("email","other@example.test"))).isZero();
        assertThat(mongoTemplate.getCollection("user_profiles").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("user_management_audit").countDocuments()).isEqualTo(1);
    }

    private MockHttpServletResponse login() {
        var response = new MockHttpServletResponse();
        auth.loginAndIssue(EMAIL,PASSWORD,response);
        return response;
    }
    private void checkAccess(MockHttpServletResponse response, int expected) throws Exception {
        clear();
        mvc.perform(get("/api/v1/auth/me").cookie(response.getCookie("ACCESS_TOKEN"))).andExpect(status().is(expected));
    }
}
