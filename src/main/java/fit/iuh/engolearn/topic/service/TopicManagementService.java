package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.exception.ResourceNotFoundException;
import fit.iuh.engolearn.shared.exception.ValidationFailedException;
import fit.iuh.engolearn.shared.web.ApiFieldError;
import fit.iuh.engolearn.topic.dto.request.TopicCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.request.TopicUpdateRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementCounters;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListItem;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemStats;
import fit.iuh.engolearn.topic.repository.TopicManagementSearchCriteria;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Topic management service (design record F05 §3.3/§3.4): create / read-one /
 * partial update / management list for TPL-UC-010 + TPL-UC-019.
 */
@Service
public class TopicManagementService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final int SECONDS_PER_MINUTE = 60;

    private final TopicRepository topicRepository;
    private final TopicItemRepository topicItemRepository;
    private final Validator validator;

    public TopicManagementService(TopicRepository topicRepository,
                                  TopicItemRepository topicItemRepository,
                                  Validator validator) {
        this.topicRepository = topicRepository;
        this.topicItemRepository = topicItemRepository;
        this.validator = validator;
    }

    /** E1 — create a new topic at DRAFT; nothing else is written (no version, no items). */
    public TopicManagementResponse create(TopicCreateRequest request) {
        validate(request);
        requireNonBlankWhenPresent(request.coverUrl(), "coverUrl");

        Topic topic = Topic.builder()
                .titleEn(trim(request.titleEn()))
                .titleVi(trim(request.titleVi()))
                .cefrLevel(request.cefr() == null ? null : CefrLevel.valueOf(request.cefr()))
                .category(request.category() == null ? null : TopicCategory.valueOf(request.category()))
                .description(request.description())
                .coverUrl(request.coverUrl())
                .build();

        Topic saved = topicRepository.save(topic);
        return toResponse(saved, TopicItemStats.EMPTY);
    }

    /** E2 — read one topic with computed fields. */
    public TopicManagementResponse get(String id) {
        Topic topic = findTopic(id);
        return toResponse(topic, topicItemRepository.statsByTopicId(id));
    }

    /**
     * E3 — partial update with the frozen merge matrix: absent = keep,
     * explicit {@code null} = clear (optionals only), present = replace
     * (names trimmed); blank/null names are rejected. In place: only the
     * {@code topics} document is written.
     */
    public TopicManagementResponse update(String id, TopicUpdateRequest request) {
        validate(request);

        Topic topic = findTopic(id);

        if (request.isProvided(TopicUpdateRequest.TITLE_EN)) {
            topic.setTitleEn(requireNonBlank(request.titleEn(), TopicUpdateRequest.TITLE_EN));
        }
        if (request.isProvided(TopicUpdateRequest.TITLE_VI)) {
            topic.setTitleVi(requireNonBlank(request.titleVi(), TopicUpdateRequest.TITLE_VI));
        }
        if (request.isProvided(TopicUpdateRequest.CEFR)) {
            topic.setCefrLevel(request.cefr() == null ? null : CefrLevel.valueOf(request.cefr()));
        }
        if (request.isProvided(TopicUpdateRequest.CATEGORY)) {
            topic.setCategory(request.category() == null ? null : TopicCategory.valueOf(request.category()));
        }
        if (request.isProvided(TopicUpdateRequest.DESCRIPTION)) {
            topic.setDescription(request.description());
        }
        if (request.isProvided(TopicUpdateRequest.COVER_URL)) {
            requireNonBlankWhenPresent(request.coverUrl(), TopicUpdateRequest.COVER_URL);
            topic.setCoverUrl(request.coverUrl());
        }

        Topic saved = topicRepository.save(topic);
        return toResponse(saved, topicItemRepository.statsByTopicId(id));
    }

    /** E4 — filtered/sorted/paged list plus counters over the unfiltered managed set. */
    public TopicManagementListResponse list(TopicManagementListQuery query) {
        validate(query);

        TopicManagementSearchCriteria criteria = new TopicManagementSearchCriteria(
                query.search(),
                query.cefr() == null ? null : CefrLevel.valueOf(query.cefr()),
                query.category() == null ? null : TopicCategory.valueOf(query.category()),
                query.status() == null ? null : STATUS_PUBLISHED.equals(query.status()));

        Pageable pageable = PageRequest.of(query.page(), query.size(), LIST_SORT);
        Page<Topic> page = topicRepository.search(criteria, pageable);

        Map<String, TopicItemStats> stats = topicItemRepository.statsByTopicIds(
                page.getContent().stream().map(Topic::getId).toList());

        List<TopicManagementListItem> items = page.getContent().stream()
                .map(topic -> toListItem(topic, stats.getOrDefault(topic.getId(), TopicItemStats.EMPTY)))
                .toList();

        long total = topicRepository.count();
        long published = topicRepository.countByLatestPublishedVersionNotNull();

        return new TopicManagementListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                new TopicManagementCounters(total, published, total - published));
    }

    private Topic findTopic(String id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + id));
    }

    private TopicManagementResponse toResponse(Topic topic, TopicItemStats stats) {
        return new TopicManagementResponse(
                topic.getId(),
                topic.getTitleEn(),
                topic.getTitleVi(),
                topic.getCefrLevel(),
                topic.getCategory(),
                topic.getDescription(),
                topic.getCoverUrl(),
                deriveStatus(topic),
                durationMinutes(stats),
                (int) stats.itemCount(),
                topic.getCreatedAt(),
                topic.getUpdatedAt());
    }

    private TopicManagementListItem toListItem(Topic topic, TopicItemStats stats) {
        return new TopicManagementListItem(
                topic.getId(),
                topic.getTitleEn(),
                topic.getTitleVi(),
                topic.getCefrLevel(),
                topic.getCategory(),
                deriveStatus(topic),
                (int) stats.itemCount(),
                topic.getUpdatedAt());
    }

    /** Derived, never stored: null latest published version ⇔ draft ({@code DEC-09}). */
    private static String deriveStatus(Topic topic) {
        return topic.getLatestPublishedVersion() == null ? STATUS_DRAFT : STATUS_PUBLISHED;
    }

    /** ceil(Σ durationSec / 60) so a non-zero topic never under-reports ({@code DEC-07}). */
    private static int durationMinutes(TopicItemStats stats) {
        return (int) Math.ceil(stats.totalDurationSec() / (double) SECONDS_PER_MINUTE);
    }

    /** Bean Validation on request DTOs — the service guard for direct (non-web) callers. */
    private void validate(Object target) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        if (violations.isEmpty()) {
            return;
        }
        List<ApiFieldError> fieldErrors = violations.stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        throw new ValidationFailedException(fieldErrors);
    }

    /** PATCH-only guard: a provided name must never be null or blank (names can never be cleared). */
    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ValidationFailedException.forField(field, field + " must not be blank");
        }
        return value.trim();
    }

    /** "Non-blank when present" for optional fields (Bean Validation cannot express this). */
    private static void requireNonBlankWhenPresent(String value, String field) {
        if (value != null && value.isBlank()) {
            throw ValidationFailedException.forField(field, field + " must not be blank when present");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
