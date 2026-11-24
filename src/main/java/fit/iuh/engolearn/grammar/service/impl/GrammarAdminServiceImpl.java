package fit.iuh.engolearn.grammar.service.impl;

import fit.iuh.engolearn.grammar.dto.response.GrammarCheckResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarChecklistResponse;
import fit.iuh.engolearn.grammar.dto.request.CreateGrammarLessonRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementLessonResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarLessonRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarStatsResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementTopicResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarTopicRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicPageResponse;
import fit.iuh.engolearn.grammar.exception.GrammarRequestException;
import fit.iuh.engolearn.grammar.mapper.GrammarAdminMapper;
import fit.iuh.engolearn.grammar.model.GrammarAuditEntry;
import fit.iuh.engolearn.grammar.model.GrammarLesson;
import fit.iuh.engolearn.grammar.model.GrammarPublishedRevision;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarArchiveReason;
import fit.iuh.engolearn.grammar.model.enums.GrammarAuditAction;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.model.vo.GrammarAuditStateVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarBlockVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarQuestionVo;
import fit.iuh.engolearn.grammar.repository.GrammarAdminRepository;
import fit.iuh.engolearn.grammar.service.GrammarAdminService;
import fit.iuh.engolearn.grammar.service.GrammarChecklistValidator;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static fit.iuh.engolearn.grammar.model.enums.GrammarAuditAction.*;
import static fit.iuh.engolearn.grammar.model.enums.GrammarStatus.*;

@Service
@PreAuthorize("hasRole('CONTENT_MANAGER')")
@RequiredArgsConstructor
public class GrammarAdminServiceImpl implements GrammarAdminService {
    private static final int MAX_ACTIVE_LESSONS = 10;

    private final GrammarAdminRepository repository;
    private final GrammarChecklistValidator validator;
    private final CurrentUserProvider currentUser;

    @Override
    public GrammarTopicPageResponse list(String search, String level, String status, int page, int size) {
        GrammarLevel requestedLevel = level == null ? null : GrammarLevel.valueOf(level);
        var results = repository.search(search, requestedLevel, status, page, size);
        var counts = repository.counts();
        long total = counts.published() + counts.inProgress() + counts.draft() + counts.archived();
        GrammarStatsResponse stats = new GrammarStatsResponse(total, counts.published(), counts.inProgress(), counts.draft(), counts.archived());
        return new GrammarTopicPageResponse(results.topics().stream().map(topic -> GrammarAdminMapper.topic(topic, false))
                .toList(), results.total(), stats);
    }

    @Override
    public GrammarManagementTopicResponse getTopic(String topicId) {
        return GrammarAdminMapper.topic(findTopic(topicId), true);
    }

    @Override
    public GrammarManagementTopicResponse createTopic(GrammarTopicRequest input) {
        return repository.transact(() -> {
            long sequence = repository.lockTopicOrder(true);
            GrammarTopic topic = GrammarTopic.builder().id(UUID.randomUUID().toString())
                    .code(String.format(Locale.ROOT, "ENG-GRM-T%04d", sequence))
                    .status(DRAFT).level(GrammarLevel.valueOf(input.level())).build();
            writeTopicBasics(topic, input);
            placeTopic(topic, input.order());
            repository.insertTopic(topic);
            return GrammarAdminMapper.topic(topic, true);
        });
    }

    @Override
    public GrammarManagementTopicResponse updateTopic(String topicId, GrammarTopicRequest input) {
        return repository.transact(() -> {
            repository.lockTopicOrder(false);
            GrammarTopic topic = findTopic(topicId);
            requireVersion(input.version(), topic.getVersion());
            requireWritableTopic(topic);
            GrammarLevel requestedLevel = GrammarLevel.valueOf(input.level());
            boolean levelChanged = topic.getLevel() != requestedLevel;
            if (levelChanged && topic.getFirstPublishedAt() != null) {
                throw conflict("GRAMMAR_LEVEL_LOCKED", "Không thể đổi cấp độ của chủ đề đã từng xuất bản.");
            }
            if (topic.getStatus() == PUBLISHED && input.description().strip().length() < 80) {
                throw invalid("GRAMMAR_DESCRIPTION_TOO_SHORT", "Mô tả chủ đề đã xuất bản cần ít nhất 80 ký tự.");
            }

            GrammarAuditStateVo before = topicState(topic);
            if (levelChanged) {
                closeTopicGap(topic);
                topic.setLevel(requestedLevel);
                placeTopic(topic, input.order());
            } else if (input.order() != null && !input.order().equals(topic.getOrder())) {
                placeTopic(topic, input.order());
            }
            writeTopicBasics(topic, input);
            repository.saveTopic(topic);
            if (topic.getStatus() == PUBLISHED && topicBasicsChanged(before, topic)) {
                auditTopic(UPDATE_TOPIC, topic, before);
            }
            return GrammarAdminMapper.topic(topic, true);
        });
    }

    @Override
    public void deleteTopic(String topicId, long version) {
        repository.transact(() -> {
            repository.lockTopicOrder(false);
            GrammarTopic topic = findTopic(topicId);
            requireVersion(version, topic.getVersion());
            if (topic.getStatus() != DRAFT || topic.getFirstPublishedAt() != null) {
                throw conflict("GRAMMAR_TOPIC_DELETE_BLOCKED", "Chỉ có thể xóa chủ đề nháp chưa từng xuất bản.");
            }
            closeTopicGap(topic);
            repository.deleteTopic(topic);
            return null;
        });
    }

    @Override
    public GrammarChecklistResponse topicChecklist(String topicId) {
        return validator.topic(findTopic(topicId));
    }

    @Override
    public GrammarManagementTopicResponse publishTopic(String topicId, long version) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            requireVersion(version, topic.getVersion());
            if (topic.getStatus() != DRAFT) {
                throw conflict("GRAMMAR_TOPIC_PUBLISH_BLOCKED", "Chỉ có thể xuất bản chủ đề ở trạng thái nháp.");
            }
            requirePassing(validator.topic(topic));
            List<GrammarLesson> lessons = orderedLessons(topic);
            for (GrammarLesson lesson : lessons) {
                requirePassing(lessonChecks(topic, lesson));
            }
            GrammarAuditStateVo before = topicState(topic);
            Instant now = Instant.now();
            topic.setStatus(PUBLISHED);
            if (topic.getFirstPublishedAt() == null) {
                topic.setFirstPublishedAt(now);
            }
            for (GrammarLesson lesson : lessons) {
                GrammarAuditStateVo lessonBefore = lessonState(lesson);
                publishLesson(topic, lesson, now);
                bumpLessonVersion(lesson);
                auditLesson(PUBLISH_LESSON, topic, lesson, lessonBefore);
            }
            repository.saveTopic(topic);
            auditTopic(PUBLISH_TOPIC, topic, before);
            return GrammarAdminMapper.topic(topic, true);
        });
    }

    @Override
    public GrammarManagementTopicResponse archiveTopic(String topicId, long version) {
        return repository.transact(() -> {
            repository.lockTopicOrder(false);
            GrammarTopic topic = findTopic(topicId);
            requireVersion(version, topic.getVersion());
            if (topic.getStatus() != PUBLISHED) {
                throw conflict("GRAMMAR_TOPIC_ARCHIVE_BLOCKED", "Chỉ có thể ngừng xuất bản chủ đề đang xuất bản.");
            }
            GrammarAuditStateVo before = topicState(topic);
            closeTopicGap(topic);
            topic.setOrderBeforeArchive(topic.getOrder());
            topic.setOrder(null);
            topic.setStatus(ARCHIVED);
            for (GrammarLesson lesson : orderedLessons(topic)) {
                GrammarAuditStateVo lessonBefore = lessonState(lesson);
                archiveLesson(lesson, GrammarArchiveReason.TOPIC_ARCHIVE);
                auditLesson(ARCHIVE_LESSON, topic, lesson, lessonBefore);
            }
            repository.saveTopic(topic);
            auditTopic(ARCHIVE_TOPIC, topic, before);
            return GrammarAdminMapper.topic(topic, true);
        });
    }

    @Override
    public GrammarManagementTopicResponse restoreTopic(String topicId, long version) {
        return repository.transact(() -> {
            repository.lockTopicOrder(false);
            GrammarTopic topic = findTopic(topicId);
            requireVersion(version, topic.getVersion());
            if (topic.getStatus() != ARCHIVED) {
                throw conflict("GRAMMAR_TOPIC_RESTORE_BLOCKED", "Chỉ có thể khôi phục chủ đề đã ngừng xuất bản.");
            }
            GrammarAuditStateVo before = topicState(topic);
            placeTopic(topic, topic.getOrderBeforeArchive());
            topic.setOrderBeforeArchive(null);
            topic.setStatus(DRAFT);
            List<GrammarLesson> restored = topic.getLessons().stream()
                    .filter(lesson -> lesson.getArchiveReason() == GrammarArchiveReason.TOPIC_ARCHIVE)
                    .sorted(Comparator.comparing(GrammarLesson::getOrderBeforeArchive)).toList();
            List<GrammarAuditStateVo> lessonStates = restored.stream()
                    .map(GrammarAdminServiceImpl::lessonState).toList();
            for (int index = 0; index < restored.size(); index++) {
                GrammarLesson lesson = restored.get(index);
                lesson.setDraft(GrammarAdminMapper.workingContent(lesson));
                lesson.setStatus(DRAFT);
                lesson.setOrder(index + 1);
                lesson.setStatusBeforeArchive(null);
                lesson.setOrderBeforeArchive(null);
                lesson.setArchiveReason(null);
                bumpLessonVersion(lesson);
            }
            for (int index = 0; index < restored.size(); index++) {
                GrammarLesson lesson = restored.get(index);
                lesson.setReady(passed(lessonChecks(topic, lesson)));
                auditLesson(RESTORE_LESSON, topic, lesson, lessonStates.get(index));
            }
            repository.saveTopic(topic);
            auditTopic(RESTORE_TOPIC, topic, before);
            return GrammarAdminMapper.topic(topic, true);
        });
    }

    @Override
    public GrammarManagementLessonResponse getLesson(String topicId, String lessonId) {
        return GrammarAdminMapper.lesson(findLesson(findTopic(topicId), lessonId));
    }

    @Override
    public GrammarManagementLessonResponse createLesson(String topicId, CreateGrammarLessonRequest input) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            requireVersion(input.version(), topic.getVersion());
            requireWritableTopic(topic);
            if (orderedLessons(topic).size() >= MAX_ACTIVE_LESSONS) {
                throw conflict("GRAMMAR_LESSON_LIMIT", "Mỗi chủ đề có tối đa 10 bài học đang hoạt động.");
            }
            long sequence = topic.getLessonSequence() + 1;
            topic.setLessonSequence(sequence);
            GrammarLesson lesson = GrammarLesson.builder().id(UUID.randomUUID().toString())
                    .code(topic.getCode() + String.format(Locale.ROOT, "-L%03d", sequence)).status(DRAFT)
                    .draft(GrammarAdminMapper.content(input.titleVi(), input.titleEn(), input.objective(),
                            input.tags(), List.of(), List.of())).build();
            topic.getLessons().add(lesson);
            placeLesson(topic, lesson, input.order());
            repository.saveTopic(topic);
            return GrammarAdminMapper.lesson(lesson);
        });
    }

    @Override
    public GrammarManagementLessonResponse updateLesson(String topicId, String lessonId, GrammarLessonRequest input) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            GrammarLesson lesson = findWritableLesson(topic, lessonId, input.version());
            Integer oldOrder = lesson.getOrder();
            GrammarAuditStateVo before = lessonState(lesson);
            if (input.order() != null && !input.order().equals(oldOrder)) {
                placeLesson(topic, lesson, input.order());
            }
            GrammarLessonContentVo content = GrammarAdminMapper.content(input.titleVi(), input.titleEn(),
                    input.objective(), input.tags(), input.blocks(), input.questions());
            requireUniqueContentIds(content);
            if (lesson.getStatus() == DRAFT || lesson.getDraft() != null || !content.equals(lesson.getPublished())) {
                lesson.setDraft(content);
            }
            if (lesson.getStatus() == DRAFT && lesson.isReady() && !passed(lessonChecks(topic, lesson))) {
                lesson.setReady(false);
            }
            bumpLessonVersion(lesson);
            repository.saveTopic(topic);
            if (lesson.getStatus() == PUBLISHED && !Objects.equals(oldOrder, lesson.getOrder())) {
                auditLesson(REORDER_LESSON, topic, lesson, before);
            }
            return GrammarAdminMapper.lesson(lesson);
        });
    }

    @Override
    public void deleteLesson(String topicId, String lessonId, long version) {
        repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            GrammarLesson lesson = findWritableLesson(topic, lessonId, version);
            if (topic.getStatus() == PUBLISHED && lesson.getStatus() == PUBLISHED
                    && topic.getLessons().stream().filter(item -> item.getStatus() == PUBLISHED).count() == 1) {
                throw conflict("GRAMMAR_LAST_PUBLISHED_LESSON", "Hãy ngừng xuất bản chủ đề để lưu trữ bài đã xuất bản cuối cùng.");
            }
            GrammarAuditStateVo before = lessonState(lesson);
            if (lesson.getFirstPublishedAt() == null) {
                topic.getLessons().remove(lesson);
                audit(DELETE_LESSON, topic, lesson.getId(), before, null);
            } else {
                archiveLesson(lesson, GrammarArchiveReason.INDIVIDUAL);
                auditLesson(ARCHIVE_LESSON, topic, lesson, before);
            }
            reindexLessons(topic, orderedLessons(topic), null);
            repository.saveTopic(topic);
            return null;
        });
    }

    @Override
    public GrammarChecklistResponse lessonChecklist(String topicId, String lessonId) {
        GrammarTopic topic = findTopic(topicId);
        return lessonChecks(topic, findLesson(topic, lessonId));
    }

    @Override
    public GrammarManagementLessonResponse readyLesson(String topicId, String lessonId, long version, boolean confirmPublish) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            GrammarLesson lesson = findWritableLesson(topic, lessonId, version);
            if (lesson.getStatus() != DRAFT) {
                throw conflict("GRAMMAR_LESSON_READY_BLOCKED", "Chỉ có thể đánh dấu sẵn sàng cho bài học nháp.");
            }
            requirePassing(lessonChecks(topic, lesson));
            boolean autoPublish = topic.getStatus() == PUBLISHED;
            if (autoPublish && !confirmPublish) {
                throw conflict("GRAMMAR_PUBLISH_CONFIRMATION_REQUIRED", "Cần xác nhận tự động xuất bản bài học.");
            }
            GrammarAuditStateVo before = lessonState(lesson);
            lesson.setReady(true);
            if (autoPublish) {
                publishLesson(topic, lesson, Instant.now());
                auditLesson(PUBLISH_LESSON, topic, lesson, before);
            }
            bumpLessonVersion(lesson);
            repository.saveTopic(topic);
            return GrammarAdminMapper.lesson(lesson);
        });
    }

    @Override
    public GrammarManagementLessonResponse applyLesson(String topicId, String lessonId, long version) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            GrammarLesson lesson = findWritableLesson(topic, lessonId, version);
            requirePublishedDraft(lesson);
            requirePassing(lessonChecks(topic, lesson));
            GrammarAuditStateVo before = lessonState(lesson);
            publishLesson(topic, lesson, Instant.now());
            bumpLessonVersion(lesson);
            repository.saveTopic(topic);
            auditLesson(UPDATE_PUBLISHED_LESSON, topic, lesson, before);
            return GrammarAdminMapper.lesson(lesson);
        });
    }

    @Override
    public GrammarManagementLessonResponse discardLessonDraft(String topicId, String lessonId, long version) {
        return repository.transact(() -> {
            GrammarTopic topic = findTopic(topicId);
            GrammarLesson lesson = findWritableLesson(topic, lessonId, version);
            requirePublishedDraft(lesson);
            lesson.setDraft(null);
            bumpLessonVersion(lesson);
            repository.saveTopic(topic);
            return GrammarAdminMapper.lesson(lesson);
        });
    }

    private void publishLesson(GrammarTopic topic, GrammarLesson lesson, Instant now) {
        GrammarLessonContentVo content = GrammarAdminMapper.workingContent(lesson);
        boolean contentChanged = !Objects.equals(lesson.getPublished(), content);
        if (lesson.getFirstPublishedAt() == null) {
            lesson.setFirstPublishedAt(now);
        }
        if (contentChanged) {
            int version = lesson.getContentVersion() + 1;
            lesson.setContentVersion(version);
            lesson.setPublished(content);
            repository.insertRevision(GrammarPublishedRevision.builder()
                    .id(lesson.getId() + ":" + version).topicId(topic.getId()).lessonId(lesson.getId())
                    .contentVersion(version).content(content).createdAt(now).build());
        }
        lesson.setDraft(null);
        lesson.setReady(true);
        lesson.setStatus(PUBLISHED);
    }

    private void placeTopic(GrammarTopic topic, Integer desired) {
        List<GrammarTopic> neighbors = otherLevelTopics(topic);
        int index = insertionIndex(desired, neighbors.size());
        for (int position = 0; position < neighbors.size(); position++) {
            int order = position < index ? position + 1 : position + 2;
            changeTopicOrder(neighbors.get(position), order);
        }
        topic.setOrder(index + 1);
    }

    private void closeTopicGap(GrammarTopic topic) {
        List<GrammarTopic> neighbors = otherLevelTopics(topic);
        for (int index = 0; index < neighbors.size(); index++) {
            changeTopicOrder(neighbors.get(index), index + 1);
        }
    }

    private List<GrammarTopic> otherLevelTopics(GrammarTopic topic) {
        return repository.activeLevelTopics(topic.getLevel()).stream()
                .filter(other -> !other.getId().equals(topic.getId())).toList();
    }

    private void changeTopicOrder(GrammarTopic topic, int order) {
        if (Objects.equals(topic.getOrder(), order)) {
            return;
        }
        GrammarAuditStateVo before = topicState(topic);
        repository.reorderTopic(topic, order, Instant.now());
        topic.setOrder(order);
        if (topic.getStatus() == PUBLISHED) {
            auditTopic(REORDER_TOPIC, topic, before);
        }
    }

    private void placeLesson(GrammarTopic topic, GrammarLesson lesson, Integer desired) {
        List<GrammarLesson> order = new ArrayList<>(orderedLessons(topic));
        order.remove(lesson);
        order.add(insertionIndex(desired, order.size()), lesson);
        reindexLessons(topic, order, lesson);
    }

    private void reindexLessons(GrammarTopic topic, List<GrammarLesson> lessons, GrammarLesson edited) {
        for (int index = 0; index < lessons.size(); index++) {
            GrammarLesson lesson = lessons.get(index);
            int order = index + 1;
            if (Objects.equals(lesson.getOrder(), order)) {
                continue;
            }
            GrammarAuditStateVo before = lessonState(lesson);
            lesson.setOrder(order);
            if (lesson != edited) {
                bumpLessonVersion(lesson);
                if (lesson.getStatus() == PUBLISHED) {
                    auditLesson(REORDER_LESSON, topic, lesson, before);
                }
            }
        }
    }

    private static List<GrammarLesson> orderedLessons(GrammarTopic topic) {
        return topic.getLessons().stream().filter(lesson -> lesson.getStatus() != ARCHIVED)
                .sorted(Comparator.comparing(GrammarLesson::getOrder, Comparator.nullsLast(Integer::compareTo))).toList();
    }

    private static int insertionIndex(Integer requested, int neighborCount) {
        return requested == null ? neighborCount : Math.min(requested - 1, neighborCount);
    }

    private GrammarChecklistResponse lessonChecks(GrammarTopic topic, GrammarLesson lesson) {
        GrammarChecklistResponse checks = validator.lesson(GrammarAdminMapper.lessonInput(lesson));
        List<GrammarLesson> ordered = orderedLessons(topic);
        for (int index = 0; index < ordered.size(); index++) {
            if (!Objects.equals(ordered.get(index).getOrder(), index + 1)) {
                List<GrammarCheckResponse> amended = new ArrayList<>(checks.checks());
                amended.set(2, new GrammarCheckResponse(amended.get(2).label(), false,
                        "Thứ tự bài học cần liên tục từ 1 và không trùng lặp."));
                return new GrammarChecklistResponse(List.copyOf(amended));
            }
        }
        return checks;
    }

    private static void writeTopicBasics(GrammarTopic topic, GrammarTopicRequest input) {
        topic.setTitleVi(input.titleVi().strip());
        topic.setTitleEn(input.titleEn().strip());
        topic.setDescription(input.description().strip());
    }

    private static boolean topicBasicsChanged(GrammarAuditStateVo before, GrammarTopic topic) {
        return !Objects.equals(before.getTitleVi(), topic.getTitleVi())
                || !Objects.equals(before.getTitleEn(), topic.getTitleEn())
                || !Objects.equals(before.getDescription(), topic.getDescription())
                || !Objects.equals(before.getOrder(), topic.getOrder());
    }

    private static void archiveLesson(GrammarLesson lesson, GrammarArchiveReason reason) {
        lesson.setOrderBeforeArchive(lesson.getOrder());
        lesson.setStatusBeforeArchive(lesson.getStatus());
        lesson.setOrder(null);
        lesson.setStatus(ARCHIVED);
        lesson.setArchiveReason(reason);
        bumpLessonVersion(lesson);
    }

    private static void bumpLessonVersion(GrammarLesson lesson) {
        lesson.setVersion(lesson.getVersion() + 1);
    }

    private GrammarTopic findTopic(String id) {
        return repository.findTopic(id).orElseThrow(() -> new GrammarRequestException(HttpStatus.NOT_FOUND,
                "GRAMMAR_TOPIC_NOT_FOUND", "Không tìm thấy chủ đề ngữ pháp."));
    }

    private static GrammarLesson findLesson(GrammarTopic topic, String id) {
        return topic.getLessons().stream().filter(lesson -> lesson.getId().equals(id)).findFirst()
                .orElseThrow(() -> new GrammarRequestException(HttpStatus.NOT_FOUND, "GRAMMAR_LESSON_NOT_FOUND",
                        "Không tìm thấy bài học ngữ pháp."));
    }

    private static GrammarLesson findWritableLesson(GrammarTopic topic, String id, long version) {
        requireWritableTopic(topic);
        GrammarLesson lesson = findLesson(topic, id);
        requireVersion(version, lesson.getVersion());
        if (lesson.getStatus() == ARCHIVED) {
            throw conflict("GRAMMAR_LESSON_ARCHIVED", "Bài học lưu trữ chỉ có thể xem.");
        }
        return lesson;
    }

    private static void requireWritableTopic(GrammarTopic topic) {
        if (topic.getStatus() == ARCHIVED) {
            throw conflict("GRAMMAR_TOPIC_ARCHIVED", "Cần khôi phục chủ đề trước khi chỉnh sửa.");
        }
    }

    private static void requirePublishedDraft(GrammarLesson lesson) {
        if (lesson.getStatus() != PUBLISHED || lesson.getDraft() == null) {
            throw conflict("GRAMMAR_DRAFT_NOT_FOUND", "Bài học chưa có bản nháp chỉnh sửa.");
        }
    }

    private static void requireVersion(Long expected, long current) {
        if (expected == null) {
            throw invalid("GRAMMAR_VERSION_REQUIRED", "Cần gửi phiên bản nội dung đã đọc.");
        }
        if (expected != current) {
            throw conflict("GRAMMAR_VERSION_CONFLICT", "Nội dung vừa thay đổi. Vui lòng tải lại và kiểm tra lại.");
        }
    }

    private static void requireUniqueContentIds(GrammarLessonContentVo content) {
        var blockIds = new HashSet<String>();
        var questionIds = new HashSet<String>();
        if (!content.getBlocks().stream().map(GrammarBlockVo::getId).allMatch(blockIds::add)
                || !content.getQuestions().stream().map(GrammarQuestionVo::getId).allMatch(questionIds::add)) {
            throw invalid("GRAMMAR_DUPLICATE_CONTENT_ID", "Mã khối nội dung và mã câu hỏi không được trùng.");
        }
    }

    private static boolean passed(GrammarChecklistResponse checklist) {
        return checklist.checks().stream().allMatch(GrammarCheckResponse::passed);
    }

    private static void requirePassing(GrammarChecklistResponse checklist) {
        if (!passed(checklist)) {
            throw new GrammarRequestException(HttpStatus.UNPROCESSABLE_CONTENT, "GRAMMAR_CHECKLIST_FAILED",
                    "Nội dung chưa đạt điều kiện. Vui lòng kiểm tra các mục chưa đạt.", checklist);
        }
    }

    private static GrammarRequestException conflict(String code, String message) {
        return new GrammarRequestException(HttpStatus.CONFLICT, code, message);
    }

    private static GrammarRequestException invalid(String code, String message) {
        return new GrammarRequestException(HttpStatus.BAD_REQUEST, code, message);
    }

    private void auditTopic(GrammarAuditAction action, GrammarTopic topic, GrammarAuditStateVo before) {
        audit(action, topic, null, before, topicState(topic));
    }

    private void auditLesson(GrammarAuditAction action, GrammarTopic topic, GrammarLesson lesson, GrammarAuditStateVo before) {
        audit(action, topic, lesson.getId(), before, lessonState(lesson));
    }

    private void audit(GrammarAuditAction action, GrammarTopic topic, String lessonId, GrammarAuditStateVo before,
                       GrammarAuditStateVo after) {
        repository.insertAudit(GrammarAuditEntry.builder().actorId(currentUser.requireCurrentUserId())
                .topicId(topic.getId()).lessonId(lessonId).action(action).before(before).after(after)
                .createdAt(Instant.now()).build());
    }

    private static GrammarAuditStateVo topicState(GrammarTopic topic) {
        return GrammarAuditStateVo.builder().status(topic.getStatus()).order(topic.getOrder()).level(topic.getLevel())
                .titleVi(topic.getTitleVi()).titleEn(topic.getTitleEn()).description(topic.getDescription()).build();
    }

    private static GrammarAuditStateVo lessonState(GrammarLesson lesson) {
        return GrammarAuditStateVo.builder().status(lesson.getStatus()).order(lesson.getOrder()).ready(lesson.isReady())
                .contentVersion(lesson.getContentVersion()).build();
    }
}
