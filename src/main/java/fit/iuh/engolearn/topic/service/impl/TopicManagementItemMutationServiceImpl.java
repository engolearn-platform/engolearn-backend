package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.dto.request.TopicItemCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemOrderRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVisibilityRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemsMutationResponse;
import fit.iuh.engolearn.topic.exception.TopicItemMutationBlockedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicRetryRuntime;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicAttempt;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemAttemptRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemProgressRepository;
import fit.iuh.engolearn.topic.service.TopicManagementItemMutationService;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicItemDraftMutationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
@Transactional
public class TopicManagementItemMutationServiceImpl implements TopicManagementItemMutationService {
    private static final Comparator<TopicItemVersion> VERSION_ORDER = Comparator
            .comparing(TopicItemVersion::getVersion, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final TopicItemRepository items;
    private final TopicItemVersionRepository versions;
    private final TopicVersionRepository releases;
    private final UserTopicItemProgressRepository progress;
    private final UserTopicItemAttemptRepository attempts;
    private final MongoTemplate mongoTemplate;
    private final TopicManagementReadService reads;
    private final TopicItemDraftMutationGuard draftGuard;

    @Override
    public TopicItemsMutationResponse create(String topicId, TopicItemCreateRequest request) {
        draftGuard.requireTopic(topicId);
        long revision = number(request.revision(), "revision");
        String title = requiredTitle(request.title());
        Integer duration = nullableInteger(request.durationMinutes(), "durationMinutes");
        validateMetadata(request.description(), duration);
        List<TopicItemVersion> history = versions.findManagementMetadata(List.of(topicId));
        int nextOrder = history.stream().map(TopicItemVersion::getOrder).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        Topic updatedTopic = draftGuard.advanceRevision(topicId, revision);

        TopicItem item = items.save(TopicItem.builder().topicId(topicId).build());
        versions.save(TopicItemVersion.builder().topicId(topicId).topicItemId(item.getId())
                .version(1).title(title).description(request.description()).durationMinutes(duration)
                .order(nextOrder).status(ContentStatus.DRAFT).build());
        return snapshot(updatedTopic);
    }

    @Override
    public TopicItemsMutationResponse update(String topicId, String itemId, Map<String, Object> request) {
        draftGuard.requireTopic(topicId);
        validateRevision(number(request.get("revision"), "revision"));
        requireItem(topicId, itemId);
        TopicItemVersion working = draftGuard.requireWritableDraft(topicId, itemId);
        validateUpdateFields(request);
        if (!request.containsKey("title") && !request.containsKey("description")
                && !request.containsKey("durationMinutes")) {
            throw blocked("ITEM_UPDATE_BLOCKED", "An item update must include at least one metadata field.");
        }
        if (request.containsKey("title")) working.setTitle(requiredTitle(string(request.get("title"), "title")));
        if (request.containsKey("description")) {
            String description = nullableString(request.get("description"), "description");
            validateMetadata(description, working.getDurationMinutes());
            working.setDescription(description);
        }
        if (request.containsKey("durationMinutes")) {
            Integer duration = nullableInteger(request.get("durationMinutes"), "durationMinutes");
            validateMetadata(working.getDescription(), duration);
            working.setDurationMinutes(duration);
        }
        Topic updatedTopic = draftGuard.advanceRevision(topicId, number(request.get("revision"), "revision"));
        Update update = new Update().set("updatedAt", Instant.now());
        if (request.containsKey("title")) update.set("title", working.getTitle());
        if (request.containsKey("description")) update.set("description", working.getDescription());
        if (request.containsKey("durationMinutes")) update.set("durationMinutes", working.getDurationMinutes());
        if (mongoTemplate.updateFirst(versionQuery(working, ContentStatus.DRAFT), update, TopicItemVersion.class)
                .getModifiedCount() != 1) throw new TopicRevisionConflictException();
        return snapshot(updatedTopic);
    }

    @Override
    public TopicItemsMutationResponse reorder(String topicId, TopicItemOrderRequest request) {
        Topic topic = draftGuard.requireTopic(topicId);
        long revision = number(request.revision(), "revision");
        if (hasPublishedRelease(topic)) {
            throw blocked("ITEM_REORDER_BLOCKED", "Item order cannot change after the Topic is published.");
        }
        List<TopicItem> topicItems = items.findAllByTopicId(topicId);
        List<String> requestedIds = request.itemIds();
        if (requestedIds == null || requestedIds.size() != topicItems.size()
                || new HashSet<>(requestedIds).size() != requestedIds.size()
                || !new HashSet<>(requestedIds).equals(topicItems.stream().map(TopicItem::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw blocked("ITEM_REORDER_BLOCKED", "The requested order must contain every Topic Item exactly once.");
        }
        List<TopicItemVersion> working = requestedIds.stream().map(id -> {
            requireItem(topicId, id);
            return draftGuard.requireWritableDraft(topicId, id);
        }).toList();
        Topic updatedTopic = draftGuard.advanceRevision(topicId, revision);
        for (int index = 0; index < working.size(); index++) {
            TopicItemVersion version = working.get(index);
            Update update = new Update().set("order", index + 1).set("updatedAt", Instant.now());
            if (mongoTemplate.updateFirst(versionQuery(version, version.getStatus()), update, TopicItemVersion.class)
                    .getModifiedCount() != 1) throw new TopicRevisionConflictException();
        }
        return snapshot(updatedTopic);
    }

    @Override
    public TopicItemsMutationResponse visibility(String topicId, String itemId, TopicItemVisibilityRequest request) {
        Topic topic = draftGuard.requireTopic(topicId);
        long revision = number(request.revision(), "revision");
        requireItem(topicId, itemId);
        if (!(request.hidden() instanceof Boolean hidden)) throw invalid("hidden", "hidden must be true or false.");
        TopicItemVersion target = requireVisibilityTarget(topic, topicId, itemId, hidden);
        ContentStatus nextStatus = hidden ? ContentStatus.HIDDEN
                : isCurrentManifestVersion(topic, target.getId()) ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        Topic updatedTopic = draftGuard.advanceRevision(topicId, revision);
        Update update = new Update().set("status", nextStatus).set("updatedAt", Instant.now());
        if (mongoTemplate.updateFirst(versionQuery(target, target.getStatus()), update, TopicItemVersion.class)
                .getModifiedCount() != 1) throw new TopicRevisionConflictException();
        return snapshot(updatedTopic);
    }

    @Override
    public TopicItemsMutationResponse delete(String topicId, String itemId, Long revision) {
        draftGuard.requireTopic(topicId);
        validateRevision(revision);
        TopicItem item = requireItem(topicId, itemId);
        List<TopicItemVersion> history = versions.findAllByTopicIdAndTopicItemId(topicId, itemId);
        if (!isSafeToDelete(history) || history.stream().anyMatch(version ->
                releases.existsByItemVersionIdsContaining(version.getId()))
                || progress.existsByTopicItemId(itemId) || attempts.existsByTopicItemId(itemId)
                || hasLearnerSnapshot(itemId)) {
            throw blocked("ITEM_DELETE_BLOCKED", "This item may have learner or published history. Hide it to preserve progress.");
        }
        Topic updatedTopic = draftGuard.advanceRevision(topicId, revision);
        versions.delete(history.getFirst());
        items.delete(item);
        return snapshot(updatedTopic);
    }

    private boolean hasLearnerSnapshot(String itemId) {
        Query topicAttempt = Query.query(new Criteria().orOperator(
                Criteria.where("itemsSnapshot.topicItemId").is(itemId),
                Criteria.where("targetTopicItemId").is(itemId),
                Criteria.where("currentTopicItemId").is(itemId)));
        Query retry = Query.query(new Criteria().orOperator(
                Criteria.where("itemsSnapshot.topicItemId").is(itemId),
                Criteria.where("currentAttempt.topicItemId").is(itemId),
                Criteria.where("completedItemIds").is(itemId)));
        return mongoTemplate.exists(topicAttempt, UserTopicAttempt.class) || mongoTemplate.exists(retry, TopicRetryRuntime.class);
    }

    private TopicItemVersion requireVisibilityTarget(Topic topic, String topicId, String itemId, boolean hide) {
        List<TopicItemVersion> history = versions.findAllByTopicIdAndTopicItemId(topicId, itemId);
        if (history.isEmpty()) throw new TopicItemNotFoundException("Topic item was not found");
        TopicItemVersion activeVersion = activeManifestVersion(topic, itemId);
        if (activeVersion != null) {
            TopicItemVersion latest = history.stream().max(VERSION_ORDER).orElseThrow();
            if (!Objects.equals(latest.getId(), activeVersion.getId())) {
                throw blocked("ITEM_VISIBILITY_BLOCKED", "This item has a newer working version; visibility of the active release is ambiguous.");
            }
            if (hide && activeVersion.getStatus() == ContentStatus.PUBLISHED) {
                if (visiblePublishedItems(topic) <= 5) {
                    throw blocked("ITEM_VISIBILITY_BLOCKED", "A Topic must keep at least 5 published items visible.");
                }
                return activeVersion;
            }
            if (!hide && activeVersion.getStatus() == ContentStatus.HIDDEN) return activeVersion;
            throw blocked("ITEM_VISIBILITY_BLOCKED", "The active published item is already in the requested visibility state.");
        }
        TopicItemVersion latest = history.stream().max(VERSION_ORDER).orElseThrow();
        boolean latestReferenced = releases.existsByItemVersionIdsContaining(latest.getId());
        if (hide && latest.getStatus() == ContentStatus.DRAFT && !latestReferenced
                && latest.getPublishedAt() == null && latest.getTopicVersionId() == null) return latest;
        if (!hide && latest.getStatus() == ContentStatus.HIDDEN && !latestReferenced
                && latest.getPublishedAt() == null && latest.getTopicVersionId() == null) return latest;
        throw blocked("ITEM_VISIBILITY_BLOCKED", "This item version is historical or has ambiguous publication state.");
    }

    private boolean isCurrentManifestVersion(Topic topic, String versionId) {
        if (topic.getLatestPublishedVersion() == null) return false;
        return releases.findById(topic.getLatestPublishedVersion())
                .filter(release -> Objects.equals(release.getTopicId(), topic.getId()))
                .filter(release -> release.getStatus() == ContentStatus.PUBLISHED)
                .map(release -> release.getItemVersionIds() != null && release.getItemVersionIds().contains(versionId))
                .orElse(false);
    }

    private TopicItemVersion activeManifestVersion(Topic topic, String itemId) {
        if (topic.getLatestPublishedVersion() == null) return null;
        TopicVersion release = releases.findById(topic.getLatestPublishedVersion())
                .filter(candidate -> Objects.equals(candidate.getTopicId(), topic.getId()))
                .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
                .orElse(null);
        if (release == null || release.getItemVersionIds() == null) return null;
        List<TopicItemVersion> matches = versions.findAllById(release.getItemVersionIds()).stream()
                .filter(version -> Objects.equals(version.getTopicId(), topic.getId()))
                .filter(version -> Objects.equals(version.getTopicItemId(), itemId)).toList();
        if (matches.size() > 1) {
            throw blocked("ITEM_VISIBILITY_BLOCKED", "The active release contains ambiguous versions for this item.");
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private int visiblePublishedItems(Topic topic) {
        if (topic.getLatestPublishedVersion() == null) return 0;
        TopicVersion release = releases.findById(topic.getLatestPublishedVersion())
                .filter(candidate -> Objects.equals(candidate.getTopicId(), topic.getId()))
                .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
                .orElse(null);
        if (release == null || release.getItemVersionIds() == null) return 0;
        return (int) versions.findAllById(release.getItemVersionIds()).stream()
                .filter(version -> Objects.equals(version.getTopicId(), topic.getId()))
                .filter(version -> version.getStatus() == ContentStatus.PUBLISHED).count();
    }

    private static Query versionQuery(TopicItemVersion version, ContentStatus status) {
        return new Query(new Criteria().andOperator(
                Criteria.where("_id").is(version.getId()),
                Criteria.where("topic_id").is(version.getTopicId()),
                Criteria.where("topic_item_id").is(version.getTopicItemId()),
                Criteria.where("status").is(status)));
    }

    private boolean hasPublishedRelease(Topic topic) {
        return topic.getLatestPublishedVersion() != null
                || releases.findAllByTopicId(topic.getId()).stream()
                .anyMatch(release -> release.getStatus() == ContentStatus.PUBLISHED
                        || release.getStatus() == ContentStatus.HIDDEN);
    }

    private static boolean isSafeToDelete(List<TopicItemVersion> history) {
        if (history.size() != 1) return false;
        TopicItemVersion only = history.getFirst();
        return only.getStatus() == ContentStatus.DRAFT
                && Objects.equals(only.getVersion(), 1)
                && only.getTopicVersionId() == null
                && only.getPreviousItemVersionId() == null
                && only.getPublishedAt() == null;
    }

    private TopicItem requireItem(String topicId, String itemId) {
        return items.findByIdAndTopicId(itemId, topicId).orElseThrow(() -> {
            throw new TopicItemNotFoundException("Topic item was not found");
        });
    }

    private TopicItemsMutationResponse snapshot(Topic topic) {
        return new TopicItemsMutationResponse(topic.getRevision() == null ? 0 : topic.getRevision(), reads.items(topic.getId()));
    }

    private static void validateRevision(Long revision) {
        if (revision == null || revision < 0) throw invalid("revision", "revision must be a non-negative integer.");
    }

    private static String requiredTitle(String title) {
        if (title == null || title.isBlank()) throw invalid("title", "title must not be blank.");
        return title.trim();
    }

    private static void validateMetadata(String description, Integer durationMinutes) {
        if (durationMinutes != null && durationMinutes < 0) {
            throw invalid("durationMinutes", "durationMinutes must not be negative.");
        }
    }

    private static void validateUpdateFields(Map<String, Object> body) {
        for (String field : body.keySet()) {
            if (!Set.of("revision", "title", "description", "durationMinutes").contains(field)) {
                throw invalid(field, "Field is not allowed");
            }
        }
    }

    private static Long number(Object value, String field) {
        if (!(value instanceof Integer || value instanceof Long)) throw invalid(field, field + " must be an integer");
        long number = ((Number) value).longValue();
        if (number < 0) throw invalid(field, field + " must not be negative");
        return number;
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String text)) throw invalid(field, field + " must be a string");
        return text;
    }

    private static String nullableString(Object value, String field) {
        return value == null ? null : string(value, field);
    }

    private static Integer nullableInteger(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof Integer integer)) throw invalid(field, field + " must be an integer");
        return integer;
    }

    private static TopicMutationValidationException invalid(String field, String message) {
        return new TopicMutationValidationException(field, message);
    }

    private static TopicItemMutationBlockedException blocked(String code, String message) {
        return new TopicItemMutationBlockedException(code, message);
    }
}
