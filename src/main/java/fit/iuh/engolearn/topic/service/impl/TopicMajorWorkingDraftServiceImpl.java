package fit.iuh.engolearn.topic.service.impl;

import tools.jackson.databind.ObjectMapper;
import fit.iuh.engolearn.topic.dto.request.TopicItemWorkingDraftRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;
import fit.iuh.engolearn.topic.exception.TopicItemMutationBlockedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicMajorWorkingDraftService;
import fit.iuh.engolearn.topic.service.TopicItemDraftMutationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicMajorWorkingDraftServiceImpl implements TopicMajorWorkingDraftService {
    private final TopicRepository topics;
    private final TopicItemRepository items;
    private final TopicItemVersionRepository versions;
    private final TopicVersionRepository releases;
    private final TopicItemDraftMutationGuard draftGuard;
    private final TopicManagementReadService reads;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public TopicItemResourceMutationResponse open(String topicId, String itemId, TopicItemWorkingDraftRequest request) {
        if (request == null || request.revision() == null || request.revision() < 0
                || request.baseItemVersionId() == null || request.baseItemVersionId().isBlank()) {
            throw blocked("WORKING_DRAFT_INVALID", "Provide the current Topic revision and base Item version ID.");
        }
        Topic topic = draftGuard.requireTopic(topicId);
        long currentRevision = topic.getRevision() == null ? 0 : topic.getRevision();
        if (currentRevision != request.revision()) throw new TopicRevisionConflictException();
        if (items.findByIdAndTopicId(itemId, topicId).isEmpty()) throw new TopicItemNotFoundException("Topic item was not found");
        TopicVersion release = topic.getLatestPublishedVersion() == null ? null : releases.findById(topic.getLatestPublishedVersion())
                .filter(candidate -> Objects.equals(candidate.getTopicId(), topicId))
                .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED).orElse(null);
        if (release == null || release.getItemVersionIds() == null
                || !release.getItemVersionIds().contains(request.baseItemVersionId())) {
            throw new TopicRevisionConflictException("The requested base is not in the current visible release.");
        }
        TopicItemVersion base = versions.findByIdAndTopicIdAndTopicItemId(request.baseItemVersionId(), topicId, itemId)
                .filter(candidate -> candidate.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new TopicRevisionConflictException("The requested base is not the current visible Item version."));
        List<TopicItemVersion> history = versions.findAllByTopicIdAndTopicItemId(topicId, itemId);
        int highestVersion = history.stream().map(TopicItemVersion::getVersion).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(base.getVersion() == null ? 0 : base.getVersion());
        List<TopicItemVersion> drafts = history.stream().filter(candidate -> candidate.getStatus() == ContentStatus.DRAFT).toList();
        if (!drafts.isEmpty()) {
            if (drafts.size() == 1) {
                TopicItemVersion existing = drafts.getFirst();
                if (Objects.equals(existing.getPreviousItemVersionId(), base.getId())
                        && existing.getChangeType() == ChangeType.MAJOR
                        && Objects.equals(existing.getVersion(), highestVersion)
                        && existing.getTopicVersionId() == null && existing.getPublishedAt() == null) {
                    return new TopicItemResourceMutationResponse(currentRevision, reads.item(topicId, itemId));
                }
            }
            throw blocked("WORKING_DRAFT_EXISTS", "An unrelated, stale or ambiguous working draft already exists for this Item.");
        }
        int nextVersion = highestVersion + 1;
        TopicItemVersion draft = objectMapper.convertValue(base, TopicItemVersion.class);
        draft.setId(null);
        draft.setCreatedAt(null);
        draft.setUpdatedAt(null);
        draft.setVersion(nextVersion);
        draft.setChangeType(ChangeType.MAJOR);
        draft.setPreviousItemVersionId(base.getId());
        draft.setStatus(ContentStatus.DRAFT);
        draft.setTopicVersionId(null);
        draft.setPublishedAt(null);
        Topic updated = draftGuard.advanceRevision(topicId, request.revision());
        TopicItemVersion saved = versions.save(draft);
        return new TopicItemResourceMutationResponse(updated.getRevision() == null ? 0 : updated.getRevision(),
                reads.item(topicId, itemId));
    }

    private static TopicItemMutationBlockedException blocked(String code, String message) {
        return new TopicItemMutationBlockedException(code, message);
    }
}
