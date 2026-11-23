package fit.iuh.engolearn.grammar.service.impl;

import fit.iuh.engolearn.grammar.dto.request.GrammarSubmitAnswerRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarAnswerResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarBlockResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarExampleResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarLessonResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarLessonSummaryResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarLevelResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarPracticeResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarRoadmapResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarSeenResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicDetailResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicResponse;
import fit.iuh.engolearn.grammar.exception.GrammarRequestException;
import fit.iuh.engolearn.grammar.mapper.GrammarPracticeMapper;
import fit.iuh.engolearn.grammar.model.GrammarLesson;
import fit.iuh.engolearn.grammar.model.GrammarLessonProgress;
import fit.iuh.engolearn.grammar.model.GrammarLevelUnlock;
import fit.iuh.engolearn.grammar.model.GrammarPracticeSession;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarLearningStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarUnlockReason;
import fit.iuh.engolearn.grammar.model.vo.GrammarBlockVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeAnswerVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeOrderVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeResultVo;
import fit.iuh.engolearn.grammar.repository.GrammarLearnerRepository;
import fit.iuh.engolearn.grammar.service.GrammarLearnerService;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class GrammarLearnerServiceImpl implements GrammarLearnerService {
    private static final GrammarLevel DEFAULT_LEVEL = GrammarLevel.A1;
    private static final Duration PRACTICE_TIMEOUT = Duration.ofMinutes(30);
    private static final int PRACTICE_QUESTION_COUNT = 5;
    private static final int PASSING_SCORE = 3;
    private static final long UNLOCK_TOPIC_PERCENT = 70;
    private final GrammarLearnerRepository repository;
    private final CurrentUserProvider currentUser;
    private final UserLevelLookup userLevels;

    @Override
    public GrammarRoadmapResponse roadmap() {
        String userId = currentUser.requireCurrentUserId();
        return repository.transactForLearner(userId, () -> {
            var catalog = catalog(userId);
            Set<GrammarLevel> unlocked = storedUnlocks(userId);
            List<GrammarLevel> newlyUnlocked = evaluateUnlocks(userId, catalog, unlocked,
                    GrammarUnlockReason.ROADMAP_REEVALUATION, Instant.now());
            List<GrammarLevelResponse> levels = Arrays.stream(GrammarLevel.values())
                    .map(level -> levelResponse(level, catalog, unlocked)).toList();
            return new GrammarRoadmapResponse(profileLevel(userId), newlyUnlocked, levels);
        });
    }

    @Override
    public GrammarTopicDetailResponse topic(String topicId) {
        String userId = currentUser.requireCurrentUserId();
        var topic = accessibleTopic(userId, topicId);
        List<LessonSlot> lessons = lessonSlots(topic, topicProgress(userId, topicId));
        List<GrammarLessonSummaryResponse> summaries = lessons.stream().map(this::lessonSummary).toList();
        return new GrammarTopicDetailResponse(topicResponse(new TopicProgress(topic, lessons), false), summaries);
    }

    @Override
    public GrammarLessonResponse lesson(String topicId, String lessonId) {
        String userId = currentUser.requireCurrentUserId();
        LessonSlot slot = accessibleLesson(userId, topicId, lessonId);
        GrammarLesson lesson = slot.lesson();
        GrammarLessonContentVo content = lesson.getPublished();
        return new GrammarLessonResponse(topicId, lessonId, slot.number(), content.getTitleVi(), content.getTitleEn(),
                content.getObjective(), slot.status(), lesson.getContentVersion(),
                content.getBlocks().stream().map(GrammarLearnerServiceImpl::blockResponse).toList());
    }

    @Override
    public GrammarSeenResponse markSeen(String topicId, String lessonId, int contentVersion) {
        String userId = currentUser.requireCurrentUserId();
        return repository.transactForLearner(userId, () -> {
            LessonSlot slot = accessibleLesson(userId, topicId, lessonId);
            if (contentVersion > slot.lesson().getContentVersion()
                    || repository.findRevision(topicId, lessonId, contentVersion).isEmpty()) {
                throw new GrammarRequestException(HttpStatus.CONFLICT, "GRAMMAR_CONTENT_VERSION_CONFLICT",
                        "Phiên bản bài học không hợp lệ. Vui lòng tải lại bài học.");
            }
            GrammarLessonProgress progress = slot.progress() == null
                    ? newProgress(userId, topicId, lessonId) : slot.progress();
            if (progress.getStatus() == GrammarLearningStatus.NOT_STARTED) {
                progress.setStatus(GrammarLearningStatus.IN_PROGRESS);
            }
            Integer previous = progress.getLastSeenContentVersion();
            int seenVersion = previous == null ? contentVersion : Math.max(contentVersion, previous);
            progress.setLastSeenContentVersion(seenVersion);
            progress.setLastStudiedAt(Instant.now());
            repository.saveProgress(progress);
            return new GrammarSeenResponse(seenVersion);
        });
    }

    @Override
    public GrammarPracticeResponse startPractice(String topicId, String lessonId, boolean restart) {
        String userId = currentUser.requireCurrentUserId();
        return repository.transactForLearner(userId, () -> {
            Instant now = Instant.now();
            var active = repository.findActiveSession(userId, topicId, lessonId);
            if (active.isPresent() && !restart && !hasExpired(active.get(), now)) {
                return resume(active.get(), now);
            }

            LessonSlot slot = accessibleLesson(userId, topicId, lessonId);
            active.ifPresent(this::abandon);
            int version = slot.lesson().getContentVersion();
            var content = publishedRevision(topicId, lessonId, version);
            if (content.getQuestions().size() != PRACTICE_QUESTION_COUNT) {
                throw revisionUnavailable();
            }
            boolean shuffle = slot.progress() != null && slot.progress().getAttemptCount() > 0;
            var session = GrammarPracticeSession.builder()
                    .id(UUID.randomUUID().toString()).userId(userId).topicId(topicId).lessonId(lessonId)
                    .contentVersion(version).status(GrammarPracticeStatus.IN_PROGRESS).lastActivityAt(now)
                    .questionOrder(GrammarPracticeQuiz.questionOrder(content, shuffle)).answers(List.of()).build();
            repository.saveSession(session);
            return practiceResponse(session, content);
        });
    }

    @Override
    public GrammarPracticeResponse practice(String sessionId) {
        String userId = currentUser.requireCurrentUserId();
        GrammarPracticeResponse response = repository.transactForLearner(userId, () -> {
            var session = ownedSession(userId, sessionId);
            Instant now = Instant.now();
            if (expireIfNeeded(session, now)) {
                return null;
            }
            return session.getStatus() == GrammarPracticeStatus.IN_PROGRESS
                    ? resume(session, now) : practiceResponse(session);
        });
        return requireUnexpired(response);
    }

    @Override
    public GrammarAnswerResponse submitAnswer(String sessionId, GrammarSubmitAnswerRequest request) {
        String userId = currentUser.requireCurrentUserId();
        GrammarAnswerResponse response = repository.transactForLearner(userId, () -> {
            var session = ownedSession(userId, sessionId);
            Instant now = Instant.now();
            if (expireIfNeeded(session, now)) {
                return null;
            }

            List<GrammarPracticeAnswerVo> answers = new ArrayList<>(session.getAnswers());
            for (int index = 0; index < answers.size(); index++) {
                GrammarPracticeAnswerVo prior = answers.get(index);
                if (prior.getRequestId().equals(request.requestId())
                        || questionId(session, index).equals(request.questionId())) {
                    return answerResponse(session, sessionRevision(session), index);
                }
            }
            if (session.getStatus() == GrammarPracticeStatus.SUBMITTED
                    || !questionId(session, answers.size()).equals(request.questionId())) {
                throw new GrammarRequestException(HttpStatus.CONFLICT, "GRAMMAR_QUESTION_OUT_OF_ORDER",
                        "Vui lòng trả lời câu hỏi hiện tại của phiên luyện tập.");
            }

            GrammarPracticeOrderVo order = session.getQuestionOrder().get(answers.size());
            var content = sessionRevision(session);
            var answer = GrammarPracticeQuiz.grade(session, order, content, request);
            answers.add(answer);
            session.setAnswers(answers);
            session.setLastActivityAt(now);
            if (answers.size() == session.getQuestionOrder().size()) {
                finish(session, now);
            }
            repository.saveSession(session);
            return answerResponse(session, content, answers.size() - 1);
        });
        return requireUnexpired(response);
    }

    private void finish(GrammarPracticeSession session, Instant now) {
        String userId = session.getUserId();
        String topicId = session.getTopicId();
        String lessonId = session.getLessonId();
        boolean published = repository.fencePublishedLesson(topicId, lessonId);
        var progress = repository.findLessonProgress(userId, lessonId)
                .orElseGet(() -> newProgress(userId, topicId, lessonId));
        int score = (int) session.getAnswers().stream().filter(GrammarPracticeAnswerVo::isCorrect).count();
        boolean passed = score >= PASSING_SCORE;
        boolean unfinished = progress.getStatus() != GrammarLearningStatus.COMPLETED;
        Instant pending = progress.getPendingCompletionAt();
        boolean newlyCompleted = published && unfinished && (passed || pending != null);
        if (newlyCompleted) {
            completeProgress(progress, pending == null ? now : pending);
        } else if (progress.getStatus() == GrammarLearningStatus.NOT_STARTED) {
            progress.setStatus(GrammarLearningStatus.IN_PROGRESS);
        }
        if (!published && unfinished && passed && pending == null) {
            progress.setPendingCompletionAt(now);
        }
        progress.setAttemptCount(progress.getAttemptCount() + 1);
        progress.setLastScore(score);
        Integer bestScore = progress.getBestScore();
        progress.setBestScore(bestScore == null ? score : Math.max(score, bestScore));
        repository.saveProgress(progress);

        boolean topicCompleted = false;
        List<GrammarLevel> newlyUnlocked = List.of();
        if (newlyCompleted) {
            GrammarTopic topic = publishedTopic(topicId);
            List<LessonSlot> slots = lessonSlots(topic, topicProgress(userId, topicId));
            topicCompleted = new TopicProgress(topic, slots).completed();
            if (topicCompleted) {
                newlyUnlocked = evaluateUnlocks(userId, catalog(userId), storedUnlocks(userId),
                        GrammarUnlockReason.TOPIC_COMPLETION, now);
            }
        }
        session.setResult(GrammarPracticeResultVo.builder().score(score).passed(passed)
                .completed(progress.getStatus() == GrammarLearningStatus.COMPLETED).topicCompleted(topicCompleted)
                .archived(!published).newlyUnlockedLevels(newlyUnlocked).build());
        session.setStatus(GrammarPracticeStatus.SUBMITTED);
    }

    private static void completeProgress(GrammarLessonProgress progress, Instant completedAt) {
        progress.setStatus(GrammarLearningStatus.COMPLETED);
        progress.setFirstCompletedAt(completedAt);
        progress.setPendingCompletionAt(null);
    }

    private static String nextLesson(List<LessonSlot> lessons, String lessonId) {
        for (int index = 0; index + 1 < lessons.size(); index++) {
            LessonSlot next = lessons.get(index + 1);
            if (lessons.get(index).lesson().getId().equals(lessonId) && next.unlocked()) {
                return next.lesson().getId();
            }
        }
        return null;
    }

    private GrammarTopic accessibleTopic(String userId, String topicId) {
        var topic = publishedTopic(topicId);
        if (!isLevelUnlocked(userId, topic.getLevel())) {
            throw new GrammarRequestException(HttpStatus.FORBIDDEN, "GRAMMAR_LEVEL_LOCKED",
                    "Bạn chưa mở khóa cấp độ của chủ đề này.");
        }
        return topic;
    }

    private boolean isLevelUnlocked(String userId, GrammarLevel level) {
        return level.ordinal() <= profileLevel(userId).ordinal() || storedUnlocks(userId).contains(level);
    }

    private GrammarLevel profileLevel(String userId) {
        // User owns the monotonic profile; advanced CEFR levels share Grammar's highest available tier.
        return userLevels.findLevel(userId).map(level -> switch (level) {
            case A1 -> GrammarLevel.A1;
            case A2 -> GrammarLevel.A2;
            case B1 -> GrammarLevel.B1;
            case B2, C1, C2 -> GrammarLevel.B2;
        }).orElse(DEFAULT_LEVEL);
    }

    private GrammarTopic publishedTopic(String topicId) {
        return repository.findPublishedTopic(topicId).orElseThrow(() ->
                new GrammarRequestException(HttpStatus.NOT_FOUND, "GRAMMAR_TOPIC_NOT_FOUND",
                        "Không tìm thấy chủ đề ngữ pháp."));
    }

    private LessonSlot accessibleLesson(String userId, String topicId, String lessonId) {
        var topic = accessibleTopic(userId, topicId);
        LessonSlot slot = lessonSlots(topic, topicProgress(userId, topicId)).stream()
                .filter(candidate -> candidate.lesson().getId().equals(lessonId)).findFirst()
                .orElseThrow(() -> new GrammarRequestException(HttpStatus.NOT_FOUND, "GRAMMAR_LESSON_NOT_FOUND",
                        "Không tìm thấy bài học ngữ pháp."));
        if (!slot.unlocked()) {
            throw new GrammarRequestException(HttpStatus.FORBIDDEN, "GRAMMAR_LESSON_LOCKED",
                    "Hoàn thành bài học ngay trước bài này để mở khóa.");
        }
        return slot;
    }

    private static List<LessonSlot> lessonSlots(GrammarTopic topic, Map<String, GrammarLessonProgress> progress) {
        List<GrammarLesson> lessons = topic.getLessons().stream()
                .filter(lesson -> lesson.getStatus() == GrammarStatus.PUBLISHED)
                .sorted(Comparator.comparing(GrammarLesson::getOrder)).toList();
        List<LessonSlot> slots = new ArrayList<>(lessons.size());
        boolean previousCompleted = true;
        for (int index = 0; index < lessons.size(); index++) {
            GrammarLesson lesson = lessons.get(index);
            GrammarLessonProgress state = progress.get(lesson.getId());
            GrammarLearningStatus status = publishedLessonStatus(state);
            boolean unlocked = previousCompleted || status != GrammarLearningStatus.NOT_STARTED;
            slots.add(new LessonSlot(lesson, index + 1, state, status, unlocked));
            previousCompleted = status == GrammarLearningStatus.COMPLETED;
        }
        return List.copyOf(slots);
    }

    private static GrammarLearningStatus publishedLessonStatus(GrammarLessonProgress progress) {
        if (progress == null) {
            return GrammarLearningStatus.NOT_STARTED;
        }
        return progress.getPendingCompletionAt() == null ? progress.getStatus() : GrammarLearningStatus.COMPLETED;
    }

    private Map<GrammarLevel, LevelProgress> catalog(String userId) {
        var progress = byLesson(repository.findProgress(userId));
        var publishedTopics = repository.findPublishedTopics();
        materializeRestoredCompletions(publishedTopics, progress);
        var topicsByLevel = publishedTopics.stream()
                .map(topic -> new TopicProgress(topic, lessonSlots(topic, progress)))
                .collect(Collectors.groupingBy(topic -> topic.topic().getLevel()));
        Map<GrammarLevel, LevelProgress> levels = new EnumMap<>(GrammarLevel.class);
        for (GrammarLevel level : GrammarLevel.values()) {
            List<TopicProgress> topics = topicsByLevel.getOrDefault(level, List.of());
            levels.put(level, new LevelProgress(topics));
        }
        return levels;
    }

    private void materializeRestoredCompletions(List<GrammarTopic> topics,
                                                Map<String, GrammarLessonProgress> progress) {
        for (GrammarTopic topic : topics) {
            for (GrammarLesson lesson : topic.getLessons()) {
                var state = progress.get(lesson.getId());
                if (lesson.getStatus() == GrammarStatus.PUBLISHED && state != null
                        && state.getPendingCompletionAt() != null
                        && repository.fencePublishedLesson(topic.getId(), lesson.getId())) {
                    completeProgress(state, state.getPendingCompletionAt());
                    repository.saveProgress(state);
                }
            }
        }
    }

    private Set<GrammarLevel> storedUnlocks(String userId) {
        Set<GrammarLevel> unlocked = EnumSet.noneOf(GrammarLevel.class);
        repository.findUnlocks(userId).forEach(unlock -> unlocked.add(unlock.getLevel()));
        return unlocked;
    }

    private List<GrammarLevel> evaluateUnlocks(String userId, Map<GrammarLevel, LevelProgress> catalog,
                                                Set<GrammarLevel> unlocked, GrammarUnlockReason reason, Instant now) {
        if (unlocked.add(DEFAULT_LEVEL)) {
            insertUnlock(userId, DEFAULT_LEVEL, GrammarUnlockReason.DEFAULT, now);
        }
        List<GrammarLevel> granted = new ArrayList<>();
        GrammarLevel[] levels = GrammarLevel.values();
        GrammarLevel profile = profileLevel(userId);
        for (GrammarLevel level : levels) {
            if (level.ordinal() <= profile.ordinal() && unlocked.add(level)) {
                insertUnlock(userId, level, GrammarUnlockReason.PLACEMENT, now);
                granted.add(level);
            }
        }
        for (int index = 1; index < levels.length; index++) {
            GrammarLevel previous = levels[index - 1];
            GrammarLevel next = levels[index];
            LevelProgress progress = catalog.get(previous);
            if (unlocked.contains(previous) && progress.total() > 0
                    && progress.completed() >= threshold(progress.total()) && unlocked.add(next)) {
                insertUnlock(userId, next, reason, now);
                granted.add(next);
            }
        }
        return List.copyOf(granted);
    }

    private void insertUnlock(String userId, GrammarLevel level, GrammarUnlockReason reason, Instant now) {
        repository.insertUnlock(GrammarLevelUnlock.builder()
                .userId(userId).level(level).unlockedAt(now).reason(reason).build());
    }

    private static GrammarLevelResponse levelResponse(GrammarLevel level, Map<GrammarLevel, LevelProgress> catalog,
                                               Set<GrammarLevel> unlocked) {
        LevelProgress progress = catalog.get(level);
        boolean isUnlocked = unlocked.contains(level);
        var topics = progress.topics().stream().map(topic -> topicResponse(topic, !isUnlocked)).toList();
        long completed = progress.completed();
        long total = progress.total();
        return new GrammarLevelResponse(level, isUnlocked, completed, total, percent(completed, total),
                isUnlocked ? null : lockHint(level, catalog, unlocked), topics);
    }

    private static String lockHint(GrammarLevel level, Map<GrammarLevel, LevelProgress> catalog,
                                   Set<GrammarLevel> unlocked) {
        GrammarLevel[] levels = GrammarLevel.values();
        GrammarLevel previous = levels[level.ordinal() - 1];
        if (!unlocked.contains(previous)) {
            GrammarLevel firstLocked = Arrays.stream(levels).filter(item -> !unlocked.contains(item))
                    .findFirst().orElseThrow();
            return "Hãy mở khóa cấp độ " + firstLocked + " trước.";
        }
        LevelProgress progress = catalog.get(previous);
        if (progress.total() == 0) {
            return "Cấp độ " + previous + " chưa có nội dung để mở khóa cấp độ này.";
        }
        long remaining = threshold(progress.total()) - progress.completed();
        return "Hoàn thành thêm " + remaining + " chủ đề ở cấp độ " + previous
                + " để mở khóa (hoàn thành ít nhất " + UNLOCK_TOPIC_PERCENT + "% chủ đề).";
    }

    private static long threshold(long total) {
        return (total * UNLOCK_TOPIC_PERCENT + 99) / 100;
    }

    private static int percent(long completed, long total) {
        return total == 0 ? 0 : (int) (completed * 100 / total);
    }

    private Map<String, GrammarLessonProgress> topicProgress(String userId, String topicId) {
        return byLesson(repository.findTopicProgress(userId, topicId));
    }

    private static Map<String, GrammarLessonProgress> byLesson(List<GrammarLessonProgress> progress) {
        return progress.stream().collect(Collectors.toMap(GrammarLessonProgress::getLessonId, Function.identity()));
    }

    private static GrammarLessonProgress newProgress(String userId, String topicId, String lessonId) {
        return GrammarLessonProgress.builder().userId(userId).topicId(topicId).lessonId(lessonId)
                .status(GrammarLearningStatus.IN_PROGRESS).build();
    }

    private static GrammarTopicResponse topicResponse(TopicProgress progress, boolean locked) {
        int completed = progress.completedLessons();
        int total = progress.lessons().size();
        var topic = progress.topic();
        String status = locked ? "LOCKED" : progress.status().name();
        return new GrammarTopicResponse(topic.getId(), topic.getTitleVi(), topic.getTitleEn(), topic.getDescription(),
                topic.getLevel(), status, completed, total, percent(completed, total));
    }

    private GrammarLessonSummaryResponse lessonSummary(LessonSlot slot) {
        var lesson = slot.lesson();
        var content = lesson.getPublished();
        var progress = slot.progress();
        Integer seen = progress == null ? null : progress.getLastSeenContentVersion();
        Integer bestScore = progress == null ? null : progress.getBestScore();
        return new GrammarLessonSummaryResponse(lesson.getId(), slot.number(), content.getTitleVi(), content.getTitleEn(),
                content.getObjective(), slot.status(), slot.unlocked(), bestScore,
                seen != null && seen < lesson.getContentVersion());
    }

    private static GrammarBlockResponse blockResponse(GrammarBlockVo block) {
        List<GrammarExampleResponse> examples = block.getExamples() == null ? List.of() : block.getExamples().stream()
                .map(example -> new GrammarExampleResponse(example.getEnglish(), example.getVietnamese(), example.getAnalysis()))
                .toList();
        List<String> rules = block.getRuleItems() == null ? List.of() : List.copyOf(block.getRuleItems());
        return new GrammarBlockResponse(block.getId(), block.getType(), block.getTitle(), block.getText(), block.getFormula(),
                examples, rules, block.getIncorrect(), block.getCorrect(), block.getExplanation());
    }

    private GrammarPracticeSession ownedSession(String userId, String sessionId) {
        return repository.findOwnedSession(userId, sessionId).orElseThrow(() ->
                new GrammarRequestException(HttpStatus.NOT_FOUND, "GRAMMAR_SESSION_NOT_FOUND",
                        "Không tìm thấy phiên luyện tập."));
    }

    private GrammarLessonContentVo publishedRevision(String topicId, String lessonId, int version) {
        return repository.findRevision(topicId, lessonId, version)
                .orElseThrow(GrammarLearnerServiceImpl::revisionUnavailable).getContent();
    }

    private GrammarLessonContentVo sessionRevision(GrammarPracticeSession session) {
        return publishedRevision(session.getTopicId(), session.getLessonId(), session.getContentVersion());
    }

    private GrammarPracticeResponse resume(GrammarPracticeSession session, Instant now) {
        session.setLastActivityAt(now);
        repository.saveSession(session);
        return practiceResponse(session);
    }

    private static String questionId(GrammarPracticeSession session, int index) {
        return GrammarPracticeMapper.questionId(session.getId(), session.getQuestionOrder().get(index).getQuestionIndex());
    }

    private GrammarAnswerResponse answerResponse(GrammarPracticeSession session, GrammarLessonContentVo content, int index) {
        return new GrammarAnswerResponse(GrammarPracticeMapper.feedback(session, content, index),
                practiceResponse(session, content));
    }

    private GrammarPracticeResponse practiceResponse(GrammarPracticeSession session) {
        return practiceResponse(session, sessionRevision(session));
    }

    private GrammarPracticeResponse practiceResponse(GrammarPracticeSession session, GrammarLessonContentVo content) {
        var result = session.getResult();
        String successor = result == null || result.isArchived() ? null : currentSuccessor(session);
        return GrammarPracticeMapper.session(session, content, expiresAt(session), successor);
    }

    private String currentSuccessor(GrammarPracticeSession session) {
        String userId = session.getUserId();
        String topicId = session.getTopicId();
        return repository.findPublishedTopic(topicId)
                .filter(topic -> isLevelUnlocked(userId, topic.getLevel()))
                .map(topic -> nextLesson(lessonSlots(topic, topicProgress(userId, topicId)), session.getLessonId()))
                .orElse(null);
    }

    private boolean expireIfNeeded(GrammarPracticeSession session, Instant now) {
        if (session.getStatus() == GrammarPracticeStatus.ABANDONED) {
            return true;
        }
        if (session.getStatus() == GrammarPracticeStatus.IN_PROGRESS && hasExpired(session, now)) {
            abandon(session);
            return true;
        }
        return false;
    }

    private void abandon(GrammarPracticeSession session) {
        session.setStatus(GrammarPracticeStatus.ABANDONED);
        repository.saveSession(session);
    }

    private static boolean hasExpired(GrammarPracticeSession session, Instant now) {
        return !expiresAt(session).isAfter(now);
    }

    private static Instant expiresAt(GrammarPracticeSession session) {
        return session.getStatus() == GrammarPracticeStatus.IN_PROGRESS
                ? session.getLastActivityAt().plus(PRACTICE_TIMEOUT) : null;
    }

    private static <T> T requireUnexpired(T response) {
        // Return from the transaction first so expiring a session persists before the HTTP error.
        if (response == null) {
            throw new GrammarRequestException(HttpStatus.CONFLICT, "SESSION_EXPIRED",
                    "Phiên luyện tập đã hết hiệu lực.");
        }
        return response;
    }

    private static GrammarRequestException revisionUnavailable() {
        return new GrammarRequestException(HttpStatus.SERVICE_UNAVAILABLE, "GRAMMAR_REVISION_UNAVAILABLE",
                "Chưa thể tải bản nội dung đã xuất bản. Vui lòng thử lại.");
    }

    private record LessonSlot(GrammarLesson lesson, int number, GrammarLessonProgress progress,
                              GrammarLearningStatus status, boolean unlocked) { }

    private record TopicProgress(GrammarTopic topic, List<LessonSlot> lessons) {
        private int completedLessons() {
            return (int) lessons.stream().filter(slot -> slot.status() == GrammarLearningStatus.COMPLETED).count();
        }

        private boolean completed() {
            return !lessons.isEmpty() && completedLessons() == lessons.size();
        }

        private GrammarLearningStatus status() {
            if (completed()) {
                return GrammarLearningStatus.COMPLETED;
            }
            return lessons.stream().anyMatch(slot -> slot.status() != GrammarLearningStatus.NOT_STARTED)
                    ? GrammarLearningStatus.IN_PROGRESS : GrammarLearningStatus.NOT_STARTED;
        }
    }

    private record LevelProgress(List<TopicProgress> topics) {
        private long completed() {
            return topics.stream().filter(TopicProgress::completed).count();
        }

        private long total() {
            return topics.stream().filter(topic -> !topic.lessons().isEmpty()).count();
        }
    }
}
