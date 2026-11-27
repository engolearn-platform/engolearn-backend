package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.dto.request.TopicPublishRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPublishResponse;
import fit.iuh.engolearn.topic.dto.response.TopicUnpublishResponse;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicPublishValidationException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicPublishReadinessService;
import fit.iuh.engolearn.topic.service.TopicPublishService;
import com.mongodb.MongoException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicPublishServiceImpl implements TopicPublishService {
    private final TopicRepository topics;
    private final TopicItemVersionRepository itemVersions;
    private final TopicVersionRepository topicVersions;
    private final TopicManagementReadService reads;
    private final TopicPublishReadinessService readiness;
    private final MongoTemplate mongo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public TopicPublishResponse publish(String topicId, TopicPublishRequest request) {
        try {
            return publishInTransaction(topicId, request);
        } catch (RuntimeException failure) {
            if (isConcurrentWrite(failure)) throw new TopicRevisionConflictException();
            throw failure;
        }
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public TopicUnpublishResponse unpublish(String topicId, TopicPublishRequest request) {
        try {
            if (request == null || request.revision() == null || request.revision() < 0) {
                throw new TopicPublishValidationException(List.of(new TopicPublishReadinessResponse.Violation(
                        "revision", "REQUIRED", "Provide the latest saved Topic revision.")));
            }
            Topic topic = topics.findById(topicId).orElseThrow(() -> new TopicNotFoundException("Topic was not found"));
            long expected = topic.getRevision() == null ? 0 : topic.getRevision();
            if (expected != request.revision()) throw new TopicRevisionConflictException();
            if (!hasPublication(topic)) throw new TopicRevisionConflictException("A Topic without a published release cannot be unpublished.");
            if (Boolean.TRUE.equals(topic.getPublicationSuspended())) throw new TopicRevisionConflictException("The Topic is already unpublished.");
            TopicVersion release = topicVersions.findById(topic.getLatestPublishedVersion())
                    .filter(candidate -> Objects.equals(candidate.getTopicId(), topicId))
                    .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
                    .orElseThrow(() -> new TopicRevisionConflictException("The current published release is unavailable."));
            Instant updatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            Criteria revisionMatch = expected == 0
                    ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                    : Criteria.where("revision").is(expected);
            Criteria activeMarker = new Criteria().orOperator(Criteria.where("publicationSuspended").is(false),
                    Criteria.where("publicationSuspended").is(null));
            Query compareAndSet = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId),
                    Criteria.where("latestPublishedVersion").is(release.getId()), revisionMatch, activeMarker));
            Topic updated = mongo.findAndModify(compareAndSet, new Update().set("publicationSuspended", true)
                            .set("revision", expected + 1).set("updatedAt", updatedAt),
                    FindAndModifyOptions.options().returnNew(true), Topic.class);
            if (updated == null) throw new TopicRevisionConflictException();
            return new TopicUnpublishResponse(topicId,
                    updated.getRevision() == null ? expected + 1 : updated.getRevision(), "unpublished", release.getId());
        } catch (RuntimeException failure) {
            if (isConcurrentWrite(failure)) throw new TopicRevisionConflictException();
            throw failure;
        }
    }
    private TopicPublishResponse publishInTransaction(String topicId, TopicPublishRequest request) {
        if (request == null || request.revision() == null || request.revision() < 0) {
            throw new TopicPublishValidationException(List.of(new TopicPublishReadinessResponse.Violation(
                    "revision", "REQUIRED", "Provide the latest saved Topic revision.")));
        }

        Topic topic = topics.findById(topicId).orElseThrow(() -> new TopicNotFoundException("Topic was not found"));
        long revision = topic.getRevision() == null ? 0 : topic.getRevision();
        if (revision != request.revision()) throw new TopicRevisionConflictException();
        if (hasPublication(topic)) return publishBatch(topic, request);
        if (topic.getLatestBetaVersion() != null || !topicVersions.findAllByTopicId(topicId).isEmpty()) {
            throw new TopicRevisionConflictException("Only an unreleased Topic can use initial publish.");
        }

        TopicPublishReadinessResponse result = readiness.readiness(topicId);
        if (!result.ready()) throw new TopicPublishValidationException(result.violations());

        List<TopicManagementItemSummary> visible = reads.items(topicId).stream()
                .filter(item -> item.status() != ContentStatus.HIDDEN)
                .sorted(Comparator.comparing(TopicManagementItemSummary::order, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TopicManagementItemSummary::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<TopicPublishReadinessResponse.Violation> candidateErrors = new ArrayList<>();
        Set<String> itemIds = new HashSet<>();
        Set<String> versionIds = new HashSet<>();
        for (var item : visible) {
            String path = "items[" + item.id() + "]";
            if (item.order() == null) candidateErrors.add(new TopicPublishReadinessResponse.Violation(
                    path + ".order", "REQUIRED", "Item order is required for publication."));
            if (item.status() != ContentStatus.DRAFT || item.version() == null || item.version() != 1
                    || item.versionId() == null || item.topicVersionId() != null || item.publishedAt() != null) {
                candidateErrors.add(new TopicPublishReadinessResponse.Violation(
                        path, "INITIAL_RELEASE_STATE", "Only an unreleased first draft can be initially published."));
            }
            if (item.id() == null || !itemIds.add(item.id()) || item.versionId() == null || !versionIds.add(item.versionId())) {
                candidateErrors.add(new TopicPublishReadinessResponse.Violation(
                        path, "DUPLICATE_CANDIDATE", "The release candidate must contain distinct Items and Item versions."));
            }
        }
        if (!candidateErrors.isEmpty()) throw new TopicPublishValidationException(candidateErrors);

        Map<String, TopicItemVersion> selectedById = new HashMap<>();
        for (TopicItemVersion candidate : itemVersions.findAllById(visible.stream().map(TopicManagementItemSummary::versionId).toList())) {
            selectedById.put(candidate.getId(), candidate);
        }
        List<TopicItemVersion> selected = new ArrayList<>();
        for (var item : visible) {
            TopicItemVersion candidate = selectedById.get(item.versionId());
            if (candidate == null || !Objects.equals(candidate.getTopicId(), topicId)
                    || !Objects.equals(candidate.getTopicItemId(), item.id())
                    || candidate.getStatus() != ContentStatus.DRAFT || !Objects.equals(candidate.getVersion(), 1)
                    || candidate.getTopicVersionId() != null || candidate.getPublishedAt() != null) {
                throw new TopicRevisionConflictException("The saved working candidate changed. Reload before publishing.");
            }
            selected.add(candidate);
        }

        int duration = selected.stream().map(TopicItemVersion::getDurationMinutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        Instant publishedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        TopicVersion release = topicVersions.save(TopicVersion.builder().topicId(topicId).version(1)
                .previousVersionId(null).status(ContentStatus.PUBLISHED)
                .itemVersionIds(selected.stream().map(TopicItemVersion::getId).toList()).publishedAt(publishedAt).build());

        for (TopicItemVersion candidate : selected) {
            candidate.setStatus(ContentStatus.PUBLISHED);
            candidate.setTopicVersionId(release.getId());
            candidate.setPublishedAt(publishedAt);
            itemVersions.save(candidate);
        }

        Criteria revisionMatch = request.revision() == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(request.revision());
        Query compareAndSet = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId),
                Criteria.where("latestPublishedVersion").is(topic.getLatestPublishedVersion()), revisionMatch));
        Update publish = new Update().set("revision", request.revision() + 1).set("latestPublishedVersion", release.getId())
                .set("durationMinutes", duration).set("updatedAt", publishedAt);
        Topic updated = mongo.findAndModify(compareAndSet, publish,
                FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) throw new TopicRevisionConflictException();

        List<TopicPublishResponse.PublishedItem> itemDtos = selected.stream().map(item ->
                new TopicPublishResponse.PublishedItem(item.getTopicItemId(), item.getId(), item.getVersion(),
                        item.getOrder(), item.getTitle(), item.getDurationMinutes())).toList();
        return new TopicPublishResponse(topicId, updated.getRevision() == null ? 1 : updated.getRevision(),
                release.getId(), 1, itemDtos, itemDtos.size(), duration, publishedAt);
    }

    private TopicPublishResponse publishBatch(Topic topic, TopicPublishRequest request) {
        String topicId = topic.getId();
        TopicVersion previous = topicVersions.findById(topic.getLatestPublishedVersion())
                .filter(candidate -> Objects.equals(candidate.getTopicId(), topicId))
                .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new TopicRevisionConflictException("The current published release is unavailable."));
        List<TopicManagementItemSummary> summaries = reads.items(topicId).stream()
                .sorted(Comparator.comparing(TopicManagementItemSummary::order, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TopicManagementItemSummary::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, TopicItemVersion> historyById = new HashMap<>();
        for (TopicItemVersion version : itemVersions.findAllByTopicId(topicId)) historyById.put(version.getId(), version);
        Map<String, TopicItemVersion> releasedByItem = new HashMap<>();
        for (String versionId : previous.getItemVersionIds() == null ? List.<String>of() : previous.getItemVersionIds()) {
            TopicItemVersion version = historyById.get(versionId);
            if (version != null && Objects.equals(version.getTopicId(), topicId)) releasedByItem.put(version.getTopicItemId(), version);
        }
        for (TopicManagementItemSummary summary : summaries) {
            TopicItemVersion released = releasedByItem.get(summary.id());
            if (released != null && released.getStatus() == ContentStatus.HIDDEN && summary.status() != ContentStatus.HIDDEN) {
                throw new TopicRevisionConflictException("A hidden Item cannot have a visible working candidate in the same release.");
            }
        }
        if (Boolean.TRUE.equals(topic.getPublicationSuspended())) validateRetainedManifest(topicId, previous, historyById, releasedByItem, summaries);
        TopicPublishReadinessResponse ready = readiness.readiness(topicId);
        if (!ready.ready()) throw new TopicPublishValidationException(ready.violations());

        List<TopicItemVersion> visible = new ArrayList<>();
        List<TopicItemVersion> toPromote = new ArrayList<>();
        List<TopicItemVersion> noopDrafts = new ArrayList<>();
        List<TopicPublishResponse.ChangedItem> changes = new ArrayList<>();
        for (TopicManagementItemSummary summary : summaries) {
            if (summary.status() == ContentStatus.HIDDEN) continue;
            TopicItemVersion candidate = historyById.get(summary.versionId());
            if (candidate == null || !Objects.equals(candidate.getTopicItemId(), summary.id())
                    || !Objects.equals(candidate.getTopicId(), topicId)) {
                throw new TopicRevisionConflictException("The saved Item candidate changed. Reload before publishing.");
            }
            TopicItemVersion released = releasedByItem.get(summary.id());
            if (released != null && released.getStatus() == ContentStatus.HIDDEN) {
                throw new TopicRevisionConflictException("A hidden Item cannot have a visible working candidate in the same release.");
            }
            if (candidate.getStatus() == ContentStatus.PUBLISHED) {
                if (released == null || !Objects.equals(released.getId(), candidate.getId()))
                    throw new TopicRevisionConflictException("A published Item no longer matches the current release.");
                visible.add(candidate);
                continue;
            }
            if (candidate.getStatus() != ContentStatus.DRAFT) throw new TopicRevisionConflictException("The candidate is not publishable.");
            if (released == null) {
                if (candidate.getVersion() == null || candidate.getVersion() != 1
                        || candidate.getPreviousItemVersionId() != null) {
                    throw new TopicRevisionConflictException("A new Item must start at version 1.");
                }
                toPromote.add(candidate);
                visible.add(candidate);
                changes.add(new TopicPublishResponse.ChangedItem(candidate.getTopicItemId(), null, candidate.getId(),
                        candidate.getVersion(), "NEW"));
            } else {
                int highestVersion = itemVersions.findAllByTopicIdAndTopicItemId(topicId, summary.id()).stream()
                        .map(TopicItemVersion::getVersion).filter(Objects::nonNull).max(Comparator.naturalOrder())
                        .orElse(released.getVersion() == null ? 0 : released.getVersion());
                if (!Objects.equals(candidate.getPreviousItemVersionId(), released.getId())
                        || !Objects.equals(candidate.getVersion(), highestVersion)
                        || candidate.getChangeType() != fit.iuh.engolearn.topic.model.enums.ChangeType.MAJOR) {
                    throw new TopicRevisionConflictException("The MAJOR working copy is not based on the current released Item version.");
                }
                if (sameContent(candidate, released)) {
                    if (candidate.getTopicVersionId() != null || candidate.getPublishedAt() != null
                            || topicVersions.existsByItemVersionIdsContaining(candidate.getId())) {
                        throw new TopicRevisionConflictException("An unchanged working copy has publication history and cannot be retired.");
                    }
                    noopDrafts.add(candidate);
                    visible.add(released);
                } else {
                    toPromote.add(candidate);
                    visible.add(candidate);
                    changes.add(new TopicPublishResponse.ChangedItem(candidate.getTopicItemId(), released.getId(), candidate.getId(),
                            candidate.getVersion(), "MAJOR"));
                }
            }
        }
        if (changes.isEmpty()) {
            if (!Boolean.TRUE.equals(topic.getPublicationSuspended())) throw new TopicRevisionConflictException(
                    "There are no effective Item changes to publish.", "NO_EFFECTIVE_CHANGES");
            validateRetainedManifest(topicId, previous, historyById, visible);
            return resumeExistingRelease(topic, request, previous, visible, noopDrafts);
        }
        visible.sort(Comparator.comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TopicItemVersion::getTopicItemId));

        Map<String, TopicItemVersion> candidateByItem = new HashMap<>();
        visible.forEach(candidate -> candidateByItem.put(candidate.getTopicItemId(), candidate));
        List<String> manifest = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (String oldId : previous.getItemVersionIds() == null ? List.<String>of() : previous.getItemVersionIds()) {
            TopicItemVersion old = historyById.get(oldId);
            if (old == null) throw new TopicRevisionConflictException("A prior release Item version is unavailable.");
            if (old.getStatus() == ContentStatus.HIDDEN) {
                manifest.add(oldId);
            } else {
                TopicItemVersion replacement = candidateByItem.get(old.getTopicItemId());
                if (replacement != null && included.add(replacement.getTopicItemId())) manifest.add(replacement.getId());
            }
        }
        for (TopicItemVersion item : visible) if (included.add(item.getTopicItemId())) manifest.add(item.getId());
        int duration = visible.stream().map(TopicItemVersion::getDurationMinutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        Instant publishedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        int nextReleaseNumber = topicVersions.findAllByTopicId(topicId).stream().map(TopicVersion::getVersion)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(previous.getVersion() == null ? 0 : previous.getVersion()) + 1;
        TopicVersion release = topicVersions.save(TopicVersion.builder().topicId(topicId).version(nextReleaseNumber)
                .previousVersionId(previous.getId()).status(ContentStatus.PUBLISHED).itemVersionIds(manifest).publishedAt(publishedAt).build());
        for (TopicItemVersion candidate : toPromote) {
            candidate.setStatus(ContentStatus.PUBLISHED);
            candidate.setTopicVersionId(release.getId());
            candidate.setPublishedAt(publishedAt);
            itemVersions.save(candidate);
        }
        retireNoopDrafts(topicId, noopDrafts);
        long expected = request.revision();
        Criteria revisionMatch = expected == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(expected);
        Query compareAndSet = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId),
                Criteria.where("latestPublishedVersion").is(previous.getId()), revisionMatch));
        Topic updated = mongo.findAndModify(compareAndSet,
                new Update().set("revision", expected + 1).set("latestPublishedVersion", release.getId())
                        .set("durationMinutes", duration).set("publicationSuspended", false).set("updatedAt", publishedAt),
                FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) throw new TopicRevisionConflictException();
        List<TopicPublishResponse.PublishedItem> itemDtos = visible.stream().map(item ->
                new TopicPublishResponse.PublishedItem(item.getTopicItemId(), item.getId(), item.getVersion(),
                        item.getOrder(), item.getTitle(), item.getDurationMinutes())).toList();
        return new TopicPublishResponse(topicId, updated.getRevision() == null ? expected + 1 : updated.getRevision(),
                release.getId(), nextReleaseNumber, itemDtos, itemDtos.size(), duration, publishedAt, previous.getId(), changes);
    }

    private void validateRetainedManifest(String topicId, TopicVersion release, Map<String, TopicItemVersion> historyById,
                                          Map<String, TopicItemVersion> releasedByItem,
                                          List<TopicManagementItemSummary> summaries) {
        Set<String> manifestIds = new HashSet<>(release.getItemVersionIds() == null ? List.of() : release.getItemVersionIds());
        if (manifestIds.size() != (release.getItemVersionIds() == null ? 0 : release.getItemVersionIds().size())) {
            throw new TopicRevisionConflictException("The current release manifest contains duplicate references.");
        }
        Set<String> stableItemIds = new HashSet<>();
        for (String versionId : release.getItemVersionIds() == null ? List.<String>of() : release.getItemVersionIds()) {
            TopicItemVersion version = historyById.get(versionId);
            if (version == null || !Objects.equals(version.getTopicId(), topicId) || version.getTopicItemId() == null
                    || !stableItemIds.add(version.getTopicItemId())
                    || (version.getStatus() != ContentStatus.PUBLISHED && version.getStatus() != ContentStatus.HIDDEN)) {
                throw new TopicRevisionConflictException("The current release manifest is incomplete or unavailable.");
            }
        }
        for (TopicManagementItemSummary summary : summaries) {
            TopicItemVersion released = releasedByItem.get(summary.id());
            if (summary.status() == ContentStatus.PUBLISHED
                    && (released == null || !Objects.equals(released.getId(), summary.versionId()))) {
                throw new TopicRevisionConflictException("The published working selection does not match the current release manifest.");
            }
            if (summary.status() == ContentStatus.HIDDEN && released != null && released.getStatus() != ContentStatus.HIDDEN) {
                throw new TopicRevisionConflictException("The hidden working selection does not match the current release manifest.");
            }
            if (released == null && historyById.values().stream().anyMatch(version ->
                    Objects.equals(version.getTopicItemId(), summary.id())
                            && (version.getPublishedAt() != null || version.getTopicVersionId() != null))) {
                throw new TopicRevisionConflictException("The current release manifest omits a previously released Item.");
            }
            if (summary.status() == ContentStatus.DRAFT && released != null) {
                TopicItemVersion candidate = historyById.get(summary.versionId());
                if (candidate == null || !Objects.equals(candidate.getPreviousItemVersionId(), released.getId())) {
                    throw new TopicRevisionConflictException("The saved working draft is not based on its current release.");
                }
            }
        }
    }
    private void validateRetainedManifest(String topicId, TopicVersion release, Map<String, TopicItemVersion> historyById,
                                          List<TopicItemVersion> visible) {
        Map<String, String> releasedVisibleByItem = new HashMap<>();
        Set<String> allItemIds = new HashSet<>();
        for (String versionId : release.getItemVersionIds() == null ? List.<String>of() : release.getItemVersionIds()) {
            TopicItemVersion version = historyById.get(versionId);
            if (version == null || !Objects.equals(version.getTopicId(), topicId) || version.getTopicItemId() == null
                    || !allItemIds.add(version.getTopicItemId())) {
                throw new TopicRevisionConflictException("The current release manifest is incomplete or ambiguous.");
            }
            if (version.getStatus() == ContentStatus.PUBLISHED) {
                releasedVisibleByItem.put(version.getTopicItemId(), version.getId());
            } else if (version.getStatus() != ContentStatus.HIDDEN) {
                throw new TopicRevisionConflictException("The current release manifest contains an unavailable Item version.");
            }
        }
        Map<String, String> selectedByItem = new HashMap<>();
        for (TopicItemVersion item : visible) {
            if (item.getStatus() != ContentStatus.PUBLISHED || selectedByItem.put(item.getTopicItemId(), item.getId()) != null) {
                throw new TopicRevisionConflictException("The current release Items do not match their manifest.");
            }
        }
        if (!releasedVisibleByItem.equals(selectedByItem)) {
            throw new TopicRevisionConflictException("The current release Items do not match their manifest.");
        }
    }

    private void retireNoopDrafts(String topicId, List<TopicItemVersion> noopDrafts) {
        for (TopicItemVersion candidate : noopDrafts) {
            if (!Objects.equals(candidate.getVersion(), itemVersions.findAllByTopicIdAndTopicItemId(topicId,
                    candidate.getTopicItemId()).stream().map(TopicItemVersion::getVersion).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null))) {
                throw new TopicRevisionConflictException("The unchanged working copy is no longer the latest version.");
            }
            itemVersions.delete(candidate);
        }
    }
    private TopicPublishResponse resumeExistingRelease(Topic topic, TopicPublishRequest request, TopicVersion release,
                                                        List<TopicItemVersion> visible, List<TopicItemVersion> noopDrafts) {
        long expected = request.revision();
        Criteria revisionMatch = expected == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(expected);
        Query compareAndSet = new Query(new Criteria().andOperator(Criteria.where("_id").is(topic.getId()),
                Criteria.where("latestPublishedVersion").is(release.getId()),
                Criteria.where("publicationSuspended").is(true), revisionMatch));
        int duration = visible.stream().map(TopicItemVersion::getDurationMinutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        Instant resumedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Topic updated = mongo.findAndModify(compareAndSet, new Update().set("publicationSuspended", false)
                        .set("revision", expected + 1).set("durationMinutes", duration).set("updatedAt", resumedAt),
                FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) throw new TopicRevisionConflictException();
        retireNoopDrafts(topic.getId(), noopDrafts);
        List<TopicPublishResponse.PublishedItem> itemDtos = visible.stream().map(item ->
                new TopicPublishResponse.PublishedItem(item.getTopicItemId(), item.getId(), item.getVersion(),
                        item.getOrder(), item.getTitle(), item.getDurationMinutes())).toList();
        return new TopicPublishResponse(topic.getId(), updated.getRevision() == null ? expected + 1 : updated.getRevision(),
                release.getId(), release.getVersion(), itemDtos, itemDtos.size(), duration, release.getPublishedAt(),
                release.getPreviousVersionId(), List.of());
    }
    private boolean sameContent(TopicItemVersion left, TopicItemVersion right) {
        ObjectNode leftNode = objectMapper.valueToTree(left);
        ObjectNode rightNode = objectMapper.valueToTree(right);
        for (String field : List.of("_id", "id", "createdAt", "updatedAt", "status", "topicVersionId", "publishedAt",
                "version", "changeType", "previousItemVersionId")) {
            leftNode.remove(field);
            rightNode.remove(field);
        }
        return leftNode.equals(rightNode);
    }

    private static boolean hasPublication(Topic topic) {
        return topic.getLatestPublishedVersion() != null && !topic.getLatestPublishedVersion().isBlank();
    }

    private static boolean isConcurrentWrite(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoFailure
                    && (mongoFailure.getCode() == 112 || mongoFailure.getCode() == 251
                    || mongoFailure.hasErrorLabel("TransientTransactionError")
                    || mongoFailure.hasErrorLabel("UnknownTransactionCommitResult"))) return true;
        }
        return false;
    }
}
