package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.contract.TopicSpeakingAccess;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse;
import fit.iuh.engolearn.topic.exception.TopicSpeakingProfileException;
import fit.iuh.engolearn.topic.mapper.TopicSpeakingProfileMapper;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicSpeakingProfile;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.repository.TopicSpeakingProfileRepository;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;
import fit.iuh.engolearn.topic.service.TopicSpeakingProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TopicSpeakingProfileServiceImpl implements TopicSpeakingProfileService, TopicSpeakingAccess {
    private final TopicSpeakingOpenability scopes;
    private final TopicSpeakingProfileRepository profiles;

    @Override
    public TopicSpeakingAccessDto resolve(String learnerId, SpeakingScopeDto scope) {
        ReleasedScope release = scopes.openableScope(learnerId, scope);
        if (release == null) return new TopicSpeakingAccessDto(TopicSpeakingAccessDto.Reason.SCOPE_FORBIDDEN, null);
        TopicSpeakingProfile state = state(scope);
        TopicSpeakingProfileDto published = published(state, release);
        Map<String, TopicSpeakingResourceDto> choices = resources(release);
        if (published == null || !published.enabled() || !violations(published, choices, "").isEmpty()) {
            return new TopicSpeakingAccessDto(TopicSpeakingAccessDto.Reason.PROFILE_DISABLED, null);
        }
        var resolved = published.selectedResourceIds().stream().map(choices::get).toList();
        return new TopicSpeakingAccessDto(null, new TopicSpeakingAccessDto.Snapshot(scope, release.title(),
                release.topicTitle(), release.topicItemTitle(), release.version(), state.getPublishedProfileVersion(),
                published, resolved));
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse read(SpeakingScopeDto scope) {
        ReleasedScope release = scopes.releasedScope(scope);
        return response(state(scope), release);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse save(SpeakingScopeDto scope, long revision, TopicSpeakingProfileDto profile) {
        ReleasedScope release = scopes.releasedScope(scope);
        TopicSpeakingProfile state = requireRevision(scope, revision);
        if (profile == null) throw failure(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", "A profile is required");
        state.setDraft(TopicSpeakingProfileMapper.toValue(profile));
        return response(write(state), release);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse publish(SpeakingScopeDto scope, long revision) {
        ReleasedScope release = scopes.releasedScope(scope);
        TopicSpeakingProfile state = requireRevision(scope, revision);
        if (release == null) throw failure(HttpStatus.CONFLICT, "CONTENT_NOT_RELEASED", "Release the scope before publishing its Speaking profile");
        if (state.getDraft() == null) throw failure(HttpStatus.CONFLICT, "PROFILE_DRAFT_REQUIRED", "Save a draft before publishing");
        var draft = TopicSpeakingProfileMapper.toDto(state.getDraft());
        var invalid = violations(draft, resources(release), "profile");
        if (!invalid.isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", invalid.getFirst().path() + " " + invalid.getFirst().message());
        }
        state.setPublished(state.getDraft());
        state.setPublishedContentVersion(release.version());
        state.setPublishedProfileVersion(revision + 1);
        return response(write(state), release);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public List<Violation> draftViolations(SpeakingScopeDto scope, String path) {
        var draft = TopicSpeakingProfileMapper.toDto(state(scope).getDraft());
        if (draft == null || !draft.enabled()) return List.of();
        return violations(draft, resources(scopes.releasedScope(scope)), path);
    }

    private TopicSpeakingProfile requireRevision(SpeakingScopeDto scope, long expected) {
        TopicSpeakingProfile state = state(scope);
        if (revision(state) != expected) throw revisionChanged();
        return state;
    }

    private TopicSpeakingProfile state(SpeakingScopeDto scope) {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(scope.topicId().getBytes(StandardCharsets.UTF_8));
        if (scope.topicItemId() != null) {
            key += "." + Base64.getUrlEncoder().withoutPadding().encodeToString(scope.topicItemId().getBytes(StandardCharsets.UTF_8));
        }
        String id = key;
        return profiles.findById(id).orElseGet(() -> TopicSpeakingProfile.builder().id(id).build());
    }

    private TopicSpeakingProfile write(TopicSpeakingProfile state) {
        try {
            return profiles.save(state);
        } catch (OptimisticLockingFailureException | DuplicateKeyException conflict) {
            throw revisionChanged();
        }
    }

    private TopicSpeakingProfileResponse response(TopicSpeakingProfile state, ReleasedScope release) {
        return new TopicSpeakingProfileResponse(revision(state), TopicSpeakingProfileMapper.toDto(state.getDraft()),
                published(state, release), release == null ? null : release.version(), List.copyOf(resources(release).values()));
    }

    private static TopicSpeakingProfileDto published(TopicSpeakingProfile state, ReleasedScope release) {
        return release != null && Objects.equals(state.getPublishedContentVersion(), release.version())
                ? TopicSpeakingProfileMapper.toDto(state.getPublished()) : null;
    }

    private static long revision(TopicSpeakingProfile state) {
        return state.getRevision() == null ? 0 : state.getRevision() + 1;
    }

    private static List<Violation> violations(TopicSpeakingProfileDto draft,
                                               Map<String, TopicSpeakingResourceDto> resources, String path) {
        if (!draft.enabled()) return List.of();
        List<Violation> violations = new ArrayList<>();
        required(violations, path, "level", draft.level() == null);
        required(violations, path, "description", blank(draft.description()));
        required(violations, path, "aiRole", blank(draft.aiRole()));
        required(violations, path, "learnerRole", blank(draft.learnerRole()));
        required(violations, path, "fallbackReply", blank(draft.fallbackReply()));
        Set<String> selected = new HashSet<>();
        for (String id : draft.selectedResourceIds()) {
            if (blank(id) || !resources.containsKey(id) || !selected.add(id)) {
                violations.add(new Violation(path + ".selectedResourceIds", "INVALID_RESOURCE",
                        "Select distinct resources from this released scope."));
                break;
            }
        }
        return violations;
    }

    private static void required(List<Violation> violations, String path, String field, boolean missing) {
        if (missing) violations.add(new Violation(path + "." + field, "REQUIRED", "This field is required."));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static Map<String, TopicSpeakingResourceDto> resources(ReleasedScope release) {
        Map<String, TopicSpeakingResourceDto> choices = new LinkedHashMap<>();
        if (release == null) return choices;
        for (TopicItemVersion item : release.resourceItems()) {
            for (var word : safe(item.getVocabularies())) {
                if (word != null && word.getSnapshot() != null) {
                    add(choices, word.getVocabId(), TopicSpeakingResourceDto.Type.VOCABULARY, word.getSnapshot().getWord());
                }
            }
            addExpressions(choices, item);
        }
        return choices;
    }

    private static void addExpressions(Map<String, TopicSpeakingResourceDto> choices, TopicItemVersion item) {
        List<ExpressionBlockVo> blocks = safe(item.getExpressions());
        for (int block = 0; block < blocks.size(); block++) {
            if (blocks.get(block) == null) continue;
            List<ExpressionPurposeVo> purposes = safe(blocks.get(block).getPurposes());
            for (int purpose = 0; purpose < purposes.size(); purpose++) {
                if (purposes.get(purpose) == null) continue;
                var examples = safe(purposes.get(purpose).getExamples());
                for (int example = 0; example < examples.size(); example++) {
                    if (examples.get(example) == null) continue;
                    String id = "expression:" + item.getTopicItemId() + ":" + block + ":" + purpose + ":" + example;
                    add(choices, id, TopicSpeakingResourceDto.Type.EXPRESSION, examples.get(example).getTextEn());
                }
            }
        }
    }

    private static void add(Map<String, TopicSpeakingResourceDto> choices, String id,
                            TopicSpeakingResourceDto.Type type, String text) {
        if (!blank(id) && !blank(text)) choices.putIfAbsent(id, new TopicSpeakingResourceDto(id, type, text));
    }

    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }

    private static TopicSpeakingProfileException revisionChanged() {
        return failure(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "The Speaking profile revision changed. Reload the profile.");
    }

    private static TopicSpeakingProfileException failure(HttpStatus status, String code, String detail) {
        return new TopicSpeakingProfileException(status, code, detail);
    }
}
