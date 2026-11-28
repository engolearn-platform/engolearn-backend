package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.service.TopicManagementMutationService;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicManagementMutationServiceImpl implements TopicManagementMutationService {
    private static final Set<String> MUTABLE_FIELDS = Set.of(
            "titleEn", "titleVi", "cefr", "category", "description", "coverUrl", "tags");
    private static final Set<String> OPTIONAL_FIELDS = Set.of("cefr", "category", "description", "coverUrl", "tags");

    private final TopicRepository topics;
    private final MongoTemplate mongoTemplate;
    private final TopicManagementReadService reads;
    private final TopicMediaService topicMedia;

    @Override
    public TopicManagementResponse create(Map<String, Object> body) {
        validateAllowedFields(body, false);
        Topic topic = Topic.builder()
                .titleEn(requiredName(body, "titleEn", 200))
                .titleVi(requiredName(body, "titleVi", 300))
                .cefrLevel(cefr(body.get("cefr"), "cefr"))
                .category(category(body.get("category"), "category"))
                .description(optionalString(body.get("description"), "description", 250))
                .coverUrl(validatedCover(null, body.get("coverUrl")))
                .tags(tags(body.get("tags")))
                .revision(0L)
                .build();
        Topic saved = topics.save(topic);
        return reads.topic(saved.getId());
    }

    @Override
    public TopicManagementResponse update(String topicId, Map<String, Object> body) {
        validateAllowedFields(body, true);
        long revision = requiredRevision(body);
        Criteria revisionMatch = revision == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(revision);
        Query query = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId), revisionMatch));

        Update update = new Update().inc("revision", 1).set("updatedAt", Instant.now());
        for (String field : MUTABLE_FIELDS) {
            if (!body.containsKey(field)) continue;
            Object value = validatedValue(field, body.get(field));
            if (field.equals("coverUrl")) value = validatedCover(topics.findById(topicId).orElse(null), value);
            update.set(modelField(field), value);
        }

        Topic updated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) {
            if (!topics.existsById(topicId)) throw new TopicNotFoundException("Topic was not found");
            throw new TopicRevisionConflictException();
        }
        return reads.topic(updated.getId()).withRevision(updated.getRevision() == null ? 0 : updated.getRevision());
    }

    private String validatedCover(Topic topic, Object value) {
        String cover = (String) validatedValue("coverUrl", value);
        boolean managed = cover != null && cover.startsWith("topic-media:");
        if (topic != null && topic.getLatestPublishedVersion() != null
                && !java.util.Objects.equals(cover, topic.getCoverUrl()) && !managed) {
            throw invalid("coverUrl", "A replacement cover must be a managed image owned by this Topic.");
        }
        if (managed
                && !topicMedia.isReference(topic, cover, fit.iuh.engolearn.topic.model.enums.TopicMediaKind.IMAGE)) {
            throw invalid("coverUrl", "Image media reference must belong to this Topic and be an image asset.");
        }
        return cover;
    }

    private static void validateAllowedFields(Map<String, Object> body, boolean patch) {
        for (String field : body.keySet()) {
            if (!field.equals("revision") && !MUTABLE_FIELDS.contains(field)) {
                throw invalid(field, "Field is not allowed");
            }
        }
        if (!patch && body.containsKey("revision")) throw invalid("revision", "revision is not allowed on create");
    }

    private static long requiredRevision(Map<String, Object> body) {
        Object value = body.get("revision");
        if (!(value instanceof Integer || value instanceof Long)) {
            throw invalid("revision", "revision must be a non-negative integer");
        }
        Number number = (Number) value;
        if (number.longValue() < 0) throw invalid("revision", "revision must be a non-negative integer");
        return number.longValue();
    }

    private static String modelField(String field) {
        return field.equals("cefr") ? "cefrLevel" : field;
    }

    private static String requiredName(Map<String, Object> body, String field, int maxLength) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw invalid(field, field + " must not be blank");
        if (text.trim().length() > maxLength) throw invalid(field, field + " exceeds " + maxLength + " characters");
        return text.trim();
    }

    private static Object validatedValue(String field, Object value) {
        if (value == null) {
            if (!OPTIONAL_FIELDS.contains(field)) throw invalid(field, field + " cannot be cleared");
            return null;
        }
        return switch (field) {
            case "titleEn" -> requiredName(Map.of(field, value), field, 200);
            case "titleVi" -> requiredName(Map.of(field, value), field, 300);
            case "cefr" -> cefr(value, field);
            case "category" -> category(value, field);
            case "description" -> optionalString(value, field, 250);
            case "coverUrl" -> optionalString(value, field, 500);
            case "tags" -> tags(value);
            default -> throw invalid(field, "Field is not allowed");
        };
    }

    private static CefrLevel cefr(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw invalid(field, field + " must be a string");
        try {
            CefrLevel level = CefrLevel.valueOf(text);
            if (level == CefrLevel.C1 || level == CefrLevel.C2) throw invalid(field, "cefr must be A1, A2, B1, or B2");
            return level;
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "cefr must be A1, A2, B1, or B2");
        }
    }

    private static TopicCategory category(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw invalid(field, "category must be a string");
        try {
            return TopicCategory.valueOf(text);
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "category must be a supported value");
        }
    }

    private static String optionalString(Object value, String field, int maxLength) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw invalid(field, field + " must be a string");
        if (text.length() > maxLength) throw invalid(field, field + " exceeds " + maxLength + " characters");
        if (field.equals("coverUrl") && text.isBlank()) throw invalid(field, "coverUrl must not be blank");
        return text;
    }

    private static List<String> tags(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> entries)) throw invalid("tags", "tags must be an array of strings");
        List<String> result = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof String tag)) throw invalid("tags", "tags must be an array of strings");
            result.add(tag);
        }
        return result;
    }

    private static TopicMutationValidationException invalid(String field, String message) {
        return new TopicMutationValidationException(field, message);
    }
}
