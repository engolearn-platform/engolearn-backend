package fit.iuh.engolearn.microskill;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.microskill.service.MicroSkillService;
import fit.iuh.engolearn.microskill.dto.request.*;
import fit.iuh.engolearn.microskill.model.MicroSkill;
import fit.iuh.engolearn.microskill.model.enums.*;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.HttpStatusCodeException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfDockerAvailable
class MicroSkillCatalogIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired MicroSkillLookup lookup;
    @Autowired MicroSkillService service;
    @Autowired JwtTokenService tokens;

    @BeforeEach
    void seed() {
        MicroSkillFixtures.seed(mongoTemplate);
        new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext())
                .resolveIndexFor(MicroSkill.class).forEach(mongoTemplate.indexOps(MicroSkill.class)::createIndex);
    }
    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void seedContainsTenStableBilingualDefinitionsAndSupportsInactiveHistory() {
        assertThat(lookup.list()).hasSize(10).allSatisfy(value -> {
            assertThat(value.id()).isEqualTo(value.code());
            assertThat(value.displayNameVi()).isNotBlank();
        });
        authenticate(UserRole.CONTENT_MANAGER);
        var before = service.detail("MS-L-GIST");
        service.update(before.id(), new UpdateMicroSkillRequest(before.revision(), "Nhan moi", "Description",
                MicroSkillStatus.INACTIVE, before.displayOrder()));
        assertThat(lookup.findByCanonicalName("Listening for Gist")).hasValueSatisfying(value -> assertThat(value.active()).isFalse());
        assertThat(lookup.retainActive("Listening for Gist")).isEmpty();
        assertThatThrownBy(() -> service.delete(before.id(), before.revision()+1))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test
    void retentionPreventsDeletionAndRevisionProtectsEdits() {
        authenticate(UserRole.ADMIN);
        var created = service.create(request("CUSTOM_ONE", "Custom one"));
        assertThat(lookup.retainActive(created.canonicalName())).isPresent();
        assertThatThrownBy(() -> service.delete(created.id(), created.revision()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        service.update(created.id(), new UpdateMicroSkillRequest(1, "New label", "New description", MicroSkillStatus.ACTIVE, 20));
        assertThatThrownBy(() -> service.update(created.id(), new UpdateMicroSkillRequest(1, "Stale", "Description", MicroSkillStatus.INACTIVE, 20)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(lookup.findById(created.id())).hasValueSatisfying(value -> {
            assertThat(value.displayNameVi()).isEqualTo("New label");
            assertThat(value.canonicalName()).isEqualTo("Custom one");
        });
    }
    @Test
    void unusedDefinitionsCanBeDeletedAndUniqueIdentityIsEnforced() {
        authenticate(UserRole.CONTENT_MANAGER);
        var created = service.create(request("CUSTOM_TWO", "Custom two"));
        assertThatThrownBy(() -> service.create(request("CUSTOM_TWO", "Another name")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> service.create(request("ANOTHER_CODE", "Custom two")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(service.delete(created.id(), created.revision()).deleted()).isTrue();
        assertThat(lookup.retainActive(created.canonicalName())).isEmpty();
    }
    @Test
    void concurrentRetentionAndDeletionCannotBothSucceed() throws Exception {
        authenticate(UserRole.ADMIN);
        var created = service.create(request("RACE_SKILL", "Race skill"));
        var repository = new fit.iuh.engolearn.microskill.repository.MicroSkillRepository(mongoTemplate);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var retained = executor.submit(() -> { start.await(); return lookup.retainActive(created.canonicalName()).isPresent(); });
            var deleted = executor.submit(() -> { start.await(); return repository.deleteUnused(created.id(),created.revision()); });
            start.countDown();
            boolean referenceWon = retained.get(10,java.util.concurrent.TimeUnit.SECONDS);
            boolean deletionWon = deleted.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(referenceWon ^ deletionWon).isTrue();
            assertThat(lookup.findById(created.id()).isPresent()).isEqualTo(referenceWon);
        }
    }

    @Test
    void springServiceProxyRejectsInsufficientRole() {
        authenticate(UserRole.USER);
        assertThat(service.list().items()).hasSize(10);
        assertThatThrownBy(() -> service.create(request("DENIED", "Denied"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("MS-L-GIST", new UpdateMicroSkillRequest(1,"Label","Description",MicroSkillStatus.INACTIVE,1)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete("MS-L-GIST",1)).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest
    @EnumSource(UserRole.class)
    void httpReadAllowsAllAuthenticatedRolesButManagementRequiresContentManager(UserRole role) {
        assertThat(http(HttpMethod.GET, "/api/micro-skills", null, role).getStatusCode().value()).isEqualTo(200);
        int expected = role == UserRole.USER ? 403 : 200;
        assertThat(http(HttpMethod.GET, "/api/management/micro-skills", null, role).getStatusCode().value()).isEqualTo(expected);
        int mutation = role == UserRole.USER ? 403 : 201;
        assertThat(http(HttpMethod.POST, "/api/management/micro-skills", """
                {"code":"HTTP_SKILL","canonicalName":"HTTP skill","displayNameVi":"Nhan",
                 "descriptionVi":"Description","skill":"READING","status":"ACTIVE","displayOrder":20}
                """, role).getStatusCode().value()).isEqualTo(mutation);
    }
    @Test
    void httpRejectsGuestsAndInvalidInput() {
        for (String route : List.of("/api/micro-skills", "/api/micro-skills/MS-L-GIST", "/api/management/micro-skills")) {
            var response = http(HttpMethod.GET, route, null, null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        }
        assertThat(http(HttpMethod.POST,"/api/management/micro-skills","{}",UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
        assertThat(http(HttpMethod.DELETE,"/api/management/micro-skills/MS-L-GIST?revision=0",null,UserRole.ADMIN).getStatusCode().value()).isEqualTo(400);
    }
    private CreateMicroSkillRequest request(String code, String name) {
        return new CreateMicroSkillRequest(code, name, "Nhan tieng Viet", "Description", MicroSkillCategory.READING, MicroSkillStatus.ACTIVE, 20);
    }
    private void authenticate(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("catalog-editor", "catalog@example.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    private ResponseEntity<String> http(HttpMethod method, String path, String body, UserRole role) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) {
            String id="catalog-editor", session=UUID.randomUUID().toString();
            mongoTemplate.getCollection("users").replaceOne(new Document("_id",id),new Document("_id",id)
                    .append("email","catalog@example.test").append("password_hash","unused").append("role",role.name()).append("locked",false),
                    new com.mongodb.client.model.ReplaceOptions().upsert(true));
            mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id",session).append("account_id",id)
                    .append("expires_at",Date.from(Instant.now().plusSeconds(3600))).append("revoked",false));
            headers.add(HttpHeaders.COOKIE,"ACCESS_TOKEN="+tokens.create(id,session,"access",900));
        }
        try { return restTemplate.exchange("http://localhost:"+port+path,method,new HttpEntity<>(body,headers),String.class); }
        catch (HttpStatusCodeException error) { return new ResponseEntity<>(error.getResponseBodyAsString(),error.getResponseHeaders(),error.getStatusCode()); }
    }
}
