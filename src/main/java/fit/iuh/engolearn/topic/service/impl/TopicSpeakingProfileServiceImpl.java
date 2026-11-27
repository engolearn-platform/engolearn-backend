package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.topic.contract.TopicSpeakingAccess;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse.ResourceGroup;
import fit.iuh.engolearn.topic.exception.TopicSpeakingProfileException;
import fit.iuh.engolearn.topic.mapper.TopicSpeakingProfileMapper;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicSpeakingProfile;
import fit.iuh.engolearn.topic.model.vo.TopicSpeakingProfileVo;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicSpeakingProfileRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
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
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TopicSpeakingProfileServiceImpl implements TopicSpeakingProfileService, TopicSpeakingAccess {
    private final TopicSpeakingOpenability scopes;
    private final TopicManagementReadService management;
    private final TopicSpeakingProfileRepository profiles;
    private final TopicVersionRepository topicVersions;
    private final TopicItemVersionRepository itemVersions;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final TopicSpeakingContextBudget budget;

    @Override
    public TopicSpeakingAccessDto resolve(String learnerId, SpeakingScopeDto scope) {
        var decision = scopes.openableScope(learnerId, scope);
        if (decision.reason() != null) return new TopicSpeakingAccessDto(decision.reason(), null);
        ReleasedScope release = decision.release();
        TopicSpeakingProfile state = state(scope);
        var catalog = TopicSpeakingResourceCatalog.build(release);
        var published = project(state.getPublished(), catalog, legacyAliases(scope, state, release, catalog));
        if (published == null || !published.enabled()) {
            return new TopicSpeakingAccessDto(TopicSpeakingAccessDto.Reason.PROFILE_DISABLED, null);
        }
        var invalid = violations(published, release, catalog);
        if (!invalid.isEmpty()) {
            return new TopicSpeakingAccessDto(denial(invalid), null);
        }
        return new TopicSpeakingAccessDto(null, new TopicSpeakingAccessDto.Snapshot(scope, release.title(),
                release.topicTitle(), release.topicItemTitle(), release.version(), state.getPublishedProfileVersion(),
                published, contextResources(published, catalog.resources())));
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse read(SpeakingScopeDto scope) {
        ReleasedScope release = release(scope);
        return response(scope, state(scope), release, source(scope, release), authoringCatalog(scope),
                TopicSpeakingResourceCatalog.build(release));
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse save(SpeakingScopeDto scope, long revision, TopicSpeakingProfileDto profile) {
        ReleasedScope release = release(scope);
        Source source = source(scope, release);
        TopicSpeakingProfile state = requireRevision(scope, revision);
        var catalog = authoringCatalog(scope);
        validateDraft(profile, state, catalog.resources());
        var releasedCatalog = TopicSpeakingResourceCatalog.build(release);
        state.setDraft(TopicSpeakingProfileMapper.toValue(profile));
        return response(scope, write(state), release, source, catalog, releasedCatalog);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public TopicSpeakingProfileResponse publish(SpeakingScopeDto scope, long revision) {
        ReleasedScope release = release(scope);
        Source source = source(scope, release);
        TopicSpeakingProfile state = requireRevision(scope, revision);
        if (release == null) {
            throw failure(HttpStatus.CONFLICT, "CONTENT_NOT_RELEASED", "Release this exact scope before publishing Speaking.");
        }
        if (state.getDraft() == null) {
            throw failure(HttpStatus.CONFLICT, "PROFILE_DRAFT_REQUIRED", "Save a draft before publishing.");
        }

        // The editor preview is advisory. Compare the authoritative release and catalog again
        // immediately before the profile CAS, without writing any Topic Learning documents.
        var catalog = TopicSpeakingResourceCatalog.build(release);
        var draft = projectDraft(state, catalog, legacyAliases(scope, state, release, catalog));
        requirePublishable(draft, release, catalog);
        var authoringCatalog = authoringCatalog(scope);
        var latest = release(scope);
        var latestCatalog = TopicSpeakingResourceCatalog.build(latest);
        if (!Objects.equals(releaseKey(release), releaseKey(latest)) || !catalog.equals(latestCatalog)) {
            throw failure(HttpStatus.CONFLICT, "CONTENT_CHANGED", "The released scope changed. Reload before publishing.");
        }

        long publishedVersion = state.getPublishedProfileVersion() == null ? 1 : state.getPublishedProfileVersion() + 1;
        state.setPublished(TopicSpeakingProfileMapper.toValue(draft));
        state.setPublishedContentVersion(release.version());
        state.setPublishedProfileVersion(publishedVersion);
        var history = state.getPublicationAudit() == null
                ? new ArrayList<TopicSpeakingProfile.Publication>() : new ArrayList<>(state.getPublicationAudit());
        history.add(new TopicSpeakingProfile.Publication(users.requireCurrentUserId(), publishedVersion,
                release.version(), clock.instant()));
        state.setPublicationAudit(history);
        return response(scope, write(state), latest, source, authoringCatalog, latestCatalog);
    }

    private TopicSpeakingResourceCatalog.Catalog authoringCatalog(SpeakingScopeDto scope) {
        return TopicSpeakingResourceCatalog.forItems(management.speakingAuthoringItems(scope.topicId(), scope.topicItemId()));
    }

    private ReleasedScope release(SpeakingScopeDto scope) {
        if (scope == null || !scope.valid()) {
            throw failure(HttpStatus.NOT_FOUND, "INVALID_SCOPE", "The Speaking scope was not found.");
        }
        return scopes.releasedScope(scope);
    }

    private static List<Object> releaseKey(ReleasedScope release) {
        if (release == null) return List.of();
        return Arrays.asList(release.version(), release.title(), release.topicTitle(), release.topicItemTitle());
    }

    private Source source(SpeakingScopeDto scope, ReleasedScope release) {
        var topic = management.topic(scope.topicId());
        String topicTitle = first(topic.titleEn(), topic.titleVi());
        if (scope.topicItemId() == null) return new Source(topicTitle, null, topic.cefr(), topic.description());
        if (release != null) {
            TopicItemVersion item = release.resourceItems().getFirst();
            return new Source(item.getTitle(), topicTitle, topic.cefr(),
                    first(item.getDescription(), item.getContext() == null ? null : item.getContext().getScenarioDescription()));
        }
        var item = management.item(scope.topicId(), scope.topicItemId());
        return new Source(item.title(), topicTitle, topic.cefr(),
                first(item.description(), item.context() == null ? null : item.context().scenarioDescription()));
    }

    private static String first(String primary, String fallback) {
        return blank(primary) ? fallback : primary;
    }

    private TopicSpeakingProfileResponse response(SpeakingScopeDto scope, TopicSpeakingProfile state,
                                                  ReleasedScope release, Source source,
                                                  TopicSpeakingResourceCatalog.Catalog authoring,
                                                  TopicSpeakingResourceCatalog.Catalog catalog) {
        var aliases = legacyAliases(scope, state, release, catalog);
        var draft = projectDraft(state, catalog, aliases);
        var published = project(state.getPublished(), catalog, aliases);
        return new TopicSpeakingProfileResponse(revision(state), draft, published,
                release == null ? null : release.version(), List.copyOf(authoring.resources().values()),
                draft == null && published == null ? source.defaults() : null, source.title(), source.parentTitle(),
                state.getPublishedProfileVersion(), authoring.groups().stream()
                        .map(group -> new ResourceGroup(group.itemId(), group.itemTitle(), group.resourceIds())).toList(),
                violations(draft, release, release == null ? authoring : catalog),
                violations(published, release, catalog),
                draft != null && draft.equals(published));
    }

    private Map<String, List<String>> legacyAliases(SpeakingScopeDto scope, TopicSpeakingProfile state,
                                                     ReleasedScope current, TopicSpeakingResourceCatalog.Catalog catalog) {
        String version = state.getPublishedContentVersion();
        var profile = TopicSpeakingProfileMapper.toDto(state.getPublished());
        if (current == null || version == null || profile == null || profile.selectedResourceIds().stream()
                .noneMatch(id -> id != null && !id.startsWith("spk1:"))) return Map.of();
        if (version.equals(current.version())) return catalog.legacyCandidates();

        // Resolve positional and bare vocabulary aliases from their original release, never
        // from the positions or vocabulary ownership in the current release.
        List<TopicItemVersion> original;
        if (scope.topicItemId() != null) {
            original = itemVersions.findByIdAndTopicIdAndTopicItemId(version, scope.topicId(), scope.topicItemId())
                    .stream().toList();
        } else {
            original = topicVersions.findById(version).filter(topic -> scope.topicId().equals(topic.getTopicId()))
                    .map(topic -> topic.getItemVersionIds() == null ? List.<TopicItemVersion>of()
                            : itemVersions.findAllById(topic.getItemVersionIds()).stream()
                            .filter(item -> scope.topicId().equals(item.getTopicId())).toList())
                    .orElseGet(List::of);
        }
        return TopicSpeakingResourceCatalog.build(new ReleasedScope(null, null, null, version, original))
                .legacyCandidates();
    }

    private static TopicSpeakingProfileDto projectDraft(TopicSpeakingProfile state,
                                                         TopicSpeakingResourceCatalog.Catalog catalog,
                                                         Map<String, List<String>> aliases) {
        // An old draft without its own provenance can inherit an alias only when its
        // complete saved value matches the profile that was actually published.
        var draft = TopicSpeakingProfileMapper.toDto(state.getDraft());
        return Objects.equals(draft, TopicSpeakingProfileMapper.toDto(state.getPublished()))
                ? project(state.getDraft(), catalog, aliases) : draft;
    }

    private static TopicSpeakingProfileDto project(TopicSpeakingProfileVo value,
                                                    TopicSpeakingResourceCatalog.Catalog catalog,
                                                    Map<String, List<String>> aliases) {
        var profile = TopicSpeakingProfileMapper.toDto(value);
        if (profile == null || aliases.isEmpty()) return profile;
        var ids = profile.selectedResourceIds().stream().map(id -> {
            if (id == null || catalog.resources().containsKey(id)) return id;
            var candidates = aliases.getOrDefault(id, List.of());
            return candidates.size() == 1 && catalog.resources().containsKey(candidates.getFirst())
                    ? candidates.getFirst() : id;
        }).toList();
        return new TopicSpeakingProfileDto(profile.enabled(), profile.level(), profile.description(), profile.aiRole(),
                profile.learnerRole(), profile.suggestedTopics(), ids, profile.fallbackReply(), profile.fallbackHint());
    }

    private static void validateDraft(TopicSpeakingProfileDto profile, TopicSpeakingProfile state,
                                      Map<String, TopicSpeakingResourceDto> resources) {
        if (profile == null || profile.suggestedTopics().stream().anyMatch(Objects::isNull)) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", "The Speaking profile has invalid fields.");
        }
        Set<String> existing = new HashSet<>();
        for (var stored : Arrays.asList(state.getDraft(), state.getPublished())) {
            if (stored != null && stored.getSelectedResourceIds() != null) existing.addAll(stored.getSelectedResourceIds());
        }
        Set<String> selected = new HashSet<>();
        for (String id : profile.selectedResourceIds()) {
            if (blank(id) || !selected.add(id) || (!resources.containsKey(id) && !existing.contains(id))) {
                var violation = new Violation("profile.selectedResourceIds", "INVALID_RESOURCE",
                        "Select distinct resources from the current editor scope.");
                throw new TopicSpeakingProfileException(HttpStatus.BAD_REQUEST, violation.code(), violation.message(),
                        List.of(violation));
            }
        }
    }

    private void requirePublishable(TopicSpeakingProfileDto profile, ReleasedScope release,
                                    TopicSpeakingResourceCatalog.Catalog catalog) {
        var invalid = violations(profile, release, catalog);
        if (!invalid.isEmpty()) {
            var first = invalid.getFirst();
            String code = invalid.stream().anyMatch(v -> v.code().equals("CONTEXT_BUDGET_EXCEEDED"))
                    ? "CONTEXT_BUDGET_EXCEEDED" : "INVALID_PROFILE";
            throw new TopicSpeakingProfileException(HttpStatus.BAD_REQUEST, code, first.message(), invalid);
        }
    }

    private List<Violation> violations(TopicSpeakingProfileDto profile, ReleasedScope release,
                                       TopicSpeakingResourceCatalog.Catalog catalog) {
        if (profile == null || !profile.enabled()) return List.of();
        List<Violation> violations = new ArrayList<>();
        required(violations, "level", profile.level() == null);
        required(violations, "description", blank(profile.description()));
        required(violations, "aiRole", blank(profile.aiRole()));
        required(violations, "learnerRole", blank(profile.learnerRole()));
        required(violations, "fallbackReply", blank(profile.fallbackReply()));
        if (!blank(profile.fallbackReply()) && profile.fallbackReply().codePoints().noneMatch(
                codePoint -> Character.isLetter(codePoint)
                        && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN)) {
            violations.add(new Violation("profile.fallbackReply", "INVALID_FALLBACK",
                    "Enter an opening reply in English."));
        }
        Set<String> selected = new HashSet<>();
        for (String id : profile.selectedResourceIds()) {
            if (blank(id) || !catalog.resources().containsKey(id) || !selected.add(id)) {
                violations.add(invalidResource());
                break;
            }
        }
        if (release != null && violations.isEmpty()
                && !budget.fits(release, profile, contextResources(profile, catalog.resources()))) {
            violations.add(new Violation("profile.selectedResourceIds", "CONTEXT_BUDGET_EXCEEDED",
                    "The Speaking context is too large. Shorten the profile or remove selected resources."));
        }
        return List.copyOf(violations);
    }

    private static List<TopicSpeakingResourceDto> contextResources(TopicSpeakingProfileDto profile,
                                                                  Map<String, TopicSpeakingResourceDto> catalog) {
        Map<List<Object>, TopicSpeakingResourceDto> distinct = new LinkedHashMap<>();
        for (String id : profile.selectedResourceIds()) {
            var resource = catalog.get(id);
            distinct.putIfAbsent(Arrays.asList(resource.type(), resource.textEn().strip(), resource.context()), resource);
        }
        return List.copyOf(distinct.values());
    }

    private static TopicSpeakingAccessDto.Reason denial(List<Violation> violations) {
        if (violations.stream().anyMatch(v -> v.code().equals("INVALID_RESOURCE"))) {
            return TopicSpeakingAccessDto.Reason.PROFILE_RESOURCES_STALE;
        }
        if (violations.stream().anyMatch(v -> v.code().equals("CONTEXT_BUDGET_EXCEEDED"))) {
            return TopicSpeakingAccessDto.Reason.CONTEXT_BUDGET_EXCEEDED;
        }
        return TopicSpeakingAccessDto.Reason.PROFILE_DISABLED;
    }

    private static Violation invalidResource() {
        return new Violation("profile.selectedResourceIds", "INVALID_RESOURCE",
                "Select distinct resources from this released scope. Remove or reselect unavailable resources.");
    }

    private TopicSpeakingProfile requireRevision(SpeakingScopeDto scope, long expected) {
        TopicSpeakingProfile state = state(scope);
        if (revision(state) != expected) throw revisionChanged();
        return state;
    }

    private TopicSpeakingProfile state(SpeakingScopeDto scope) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String topicKey = encoder.encodeToString(scope.topicId().getBytes(StandardCharsets.UTF_8));
        String id = scope.topicItemId() == null ? topicKey
                : topicKey + "." + encoder.encodeToString(scope.topicItemId().getBytes(StandardCharsets.UTF_8));
        return profiles.findById(id).orElseGet(() -> TopicSpeakingProfile.builder().id(id).build());
    }

    private TopicSpeakingProfile write(TopicSpeakingProfile state) {
        try {
            return profiles.save(state);
        } catch (OptimisticLockingFailureException | DuplicateKeyException conflict) {
            throw revisionChanged();
        }
    }

    private static long revision(TopicSpeakingProfile state) {
        return state.getRevision() == null ? 0 : state.getRevision() + 1;
    }

    private static void required(List<Violation> violations, String field, boolean missing) {
        if (missing) violations.add(new Violation("profile." + field, "REQUIRED", "This field is required."));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static TopicSpeakingProfileException revisionChanged() {
        return failure(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "The Speaking profile revision changed. Reload the profile.");
    }

    private static TopicSpeakingProfileException failure(HttpStatus status, String code, String detail) {
        return new TopicSpeakingProfileException(status, code, detail);
    }

    private record Source(String title, String parentTitle, CefrLevel level, String description) {
        TopicSpeakingProfileDto defaults() {
            return new TopicSpeakingProfileDto(false, level, description == null ? "" : description, "", "",
                    blank(title) ? List.of() : List.of(title), List.of(), "", null);
        }
    }
}
