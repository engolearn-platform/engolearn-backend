package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.exception.TopicItemMutationBlockedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicItemDraftMutationGuard {
    private static final Comparator<TopicItemVersion> VERSION_ORDER = Comparator
            .comparing(TopicItemVersion::getVersion, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final TopicRepository topics;
    private final TopicItemRepository items;
    private final TopicItemVersionRepository versions;
    private final TopicVersionRepository releases;
    private final MongoTemplate mongoTemplate;

    public Topic requireTopic(String topicId) {
        return topics.findById(topicId).orElseThrow(() -> new TopicNotFoundException("Topic was not found"));
    }

    public TopicItemVersion requireWritableDraft(String topicId, String itemId) {
        if (items.findByIdAndTopicId(itemId, topicId).isEmpty()) {
            throw new TopicItemNotFoundException("Topic item was not found");
        }
        List<TopicItemVersion> history = versions.findAllByTopicIdAndTopicItemId(topicId, itemId);
        TopicItemVersion latest = history.stream().max(VERSION_ORDER)
                .orElseThrow(() -> new TopicItemMutationBlockedException(
                        "ITEM_UPDATE_BLOCKED", "No working draft exists for this item."));
        if (latest.getStatus() != ContentStatus.DRAFT
                || latest.getPublishedAt() != null
                || latest.getTopicVersionId() != null
                || releases.existsByItemVersionIdsContaining(latest.getId())) {
            throw new TopicItemMutationBlockedException(
                    "ITEM_UPDATE_BLOCKED", "Only an unpublished working draft item can be edited.");
        }
        return latest;
    }

    public Topic advanceRevision(String topicId, long expectedRevision) {
        Criteria revisionMatch = expectedRevision == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(expectedRevision);
        Query query = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId), revisionMatch));
        Update update = new Update().inc("revision", 1).set("updatedAt", Instant.now());
        Topic updated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) {
            if (!topics.existsById(topicId)) throw new TopicNotFoundException("Topic was not found");
            throw new TopicRevisionConflictException();
        }
        return updated;
    }
}
