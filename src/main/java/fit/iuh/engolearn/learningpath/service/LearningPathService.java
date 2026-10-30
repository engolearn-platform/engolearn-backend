
package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathListItemResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathListResponse;
import fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathComparedTo;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathConfig;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayResult;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillWeekStats;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathOverallWeekStats;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathProgress;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSummary;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSummaryCompletion;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSummaryMicroSkill;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeekStats;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningPathService {

    private static final List<String> SEARCHABLE_STATUSES = List.of(
            "NOT_STARTED", "IN_PROGRESS", "COMPLETED"
    );
    private static final DateTimeFormatter SEARCH_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final LearningPathRepository repository;

    public LearningPathService(LearningPathRepository repository) {
        this.repository = repository;
    }

    public LearningPathListResponse getByUserId(
            String userId,
            LearningPathListStatus status
    ) {
        ObjectId userObjectId = new ObjectId(userId);

        List<LearningPathListItemResponse> items = repository
                .findByUserIdAndStatusInOrderByCreatedAtDesc(userObjectId, statusesFor(status))
                .stream()
                .map(this::toListItemResponse)
                .toList();

        return new LearningPathListResponse(items, countsFor(userObjectId));
    }

    public LearningPathListResponse search(String userId, String keyword) {
        ObjectId userObjectId = new ObjectId(userId);
        String normalizedKeyword = keyword.trim();

        List<LearningPath> matches = new ArrayList<>(repository
                .findByUserIdAndStatusInAndMicroSkillsMicroSkillContainingIgnoreCaseOrderByCreatedAtDesc(
                        userObjectId,
                        SEARCHABLE_STATUSES,
                        normalizedKeyword
                ));

        parseSearchDate(normalizedKeyword).ifPresent(createdDate -> matches.addAll(
                repository.findByUserIdAndStatusInAndCreatedDateOrderByCreatedAtDesc(
                        userObjectId,
                        SEARCHABLE_STATUSES,
                        createdDate
                )
        ));

        Map<String, LearningPath> uniqueMatches = new LinkedHashMap<>();
        matches.forEach(path -> uniqueMatches.putIfAbsent(path.getId(), path));

        List<LearningPathListItemResponse> items = uniqueMatches.values().stream()
                .sorted(Comparator.comparing(
                        LearningPath::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toListItemResponse)
                .toList();

        return new LearningPathListResponse(items, countsFor(userObjectId));
    }

    private LearningPathListResponse.SectionCounts countsFor(ObjectId userId) {
        return new LearningPathListResponse.SectionCounts(
                repository.countByUserIdAndStatusIn(
                        userId,
                        List.of("NOT_STARTED", "IN_PROGRESS")
                ),
                repository.countByUserIdAndStatus(userId, "COMPLETED"),
                repository.countByUserIdAndStatus(userId, "CANCELLED")
        );
    }

    private Optional<String> parseSearchDate(String keyword) {
        try {
            return Optional.of(LocalDate.parse(keyword, SEARCH_DATE_FORMAT).toString());
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private List<String> statusesFor(LearningPathListStatus status) {
        return switch (status) {
            case ACTIVE -> List.of("NOT_STARTED", "IN_PROGRESS");
            case COMPLETED -> List.of("COMPLETED");
            case CANCELLED -> List.of("CANCELLED");
        };
    }

    private LearningPathListItemResponse toListItemResponse(LearningPath path) {
        List<String> skills =
                path.getMicroSkills() == null
                        ? List.of()
                        : path.getMicroSkills().stream()
                        .map(LearningPathMicroSkillSnapshot::getMicroSkill)
                        .toList();

        LearningPathProgress sourceProgress = path.getProgress();

        LearningPathListItemResponse.ProgressSummary progress =
                sourceProgress == null
                        ? null
                        : new LearningPathListItemResponse.ProgressSummary(
                        sourceProgress.getAssigned(),
                        sourceProgress.getAnswered()
                );

        String sourceAttemptId = path.getSource() == null || path.getSource().getAttemptId() == null
                ? null
                : path.getSource().getAttemptId().toHexString();

        return new LearningPathListItemResponse(
                path.getId(),
                sourceAttemptId,
                path.getCreatedDate(),
                path.getLevel(),
                path.getK(),
                skills,
                path.getStatus().name(),
                path.getCurrentWeekNo(),
                progress
        );
    }

    public LearningPathDetailResponse getDetail(String pathId, String userId) {
        ObjectId pathObjectId = new ObjectId(pathId);
        ObjectId userObjectId = new ObjectId(userId);

        LearningPath path = repository.findByIdAndUserId(pathObjectId, userObjectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy Learning Path"
                ));

        if (LearningPathStatus.CANCELLED == path.getStatus()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Learning Path đã bị hủy và không thể mở"
            );
        }

        return toDetailResponse(path);
    }

    private LearningPathDetailResponse toDetailResponse(LearningPath path) {
        LearningPathConfig config = path.getConfig();

        LearningPathDetailResponse.ConfigInfo configInfo =
                config == null
                        ? null
                        : new LearningPathDetailResponse.ConfigInfo(
                        config.getDurationWeeks(),
                        config.getQuestionsPerDay(),
                        config.getQuestionsPerSkillPerWeek()
                );

        List<LearningPathDetailResponse.MicroSkillInfo> microSkills =
                (path.getMicroSkills() == null
                        ? Collections.<LearningPathMicroSkillSnapshot>emptyList()
                        : path.getMicroSkills())
                        .stream()
                        .map(skill -> {
                            var baseline = skill.getBaseline();

                            LearningPathDetailResponse.BaselineInfo baselineInfo =
                                    baseline == null
                                            ? null
                                            : new LearningPathDetailResponse.BaselineInfo(
                                            baseline.getPercent()
                                    );

                            return new LearningPathDetailResponse.MicroSkillInfo(
                                    skill.getMicroSkill(),
                                    skill.getSkill(),
                                    baselineInfo
                            );
                        })
                        .toList();

        List<LearningPathDetailResponse.WeekInfo> weeks =
                (path.getWeeks() == null
                        ? Collections.<LearningPathWeek>emptyList()
                        : path.getWeeks())
                        .stream()
                        .map(week -> toWeekInfo(path, week))
                        .toList();

        LearningPathDetailResponse.SummaryInfo summary =
                LearningPathStatus.COMPLETED == path.getStatus() && path.getSummary() != null
                        ? toSummaryInfo(path.getSummary())
                        : null;

        return new LearningPathDetailResponse(
                path.getId(),
                path.getCreatedDate(),
                path.getLevel(),
                path.getK(),
                path.getStatus().name(),
                path.getCurrentWeekNo(),
                configInfo,
                microSkills,
                toProgressInfo(path.getProgress()),
                weeks,
                summary
        );
    }

    private LearningPathDetailResponse.WeekInfo toWeekInfo(
            LearningPath path,
            LearningPathWeek week
    ) {
        List<LearningPathDetailResponse.DayInfo> days =
                LearningPathWeekStatus.LOCKED == week.getStatus()
                        ? List.of()
                        : (week.getDays() == null
                        ? Collections.<LearningPathDay>emptyList()
                        : week.getDays())
                        .stream()
                        .map(day -> toDayInfo(path, day))
                        .toList();

        LearningPathDetailResponse.WeekStatsInfo stats =
                LearningPathWeekStatus.CLOSED == week.getStatus() && week.getStats() != null
                        ? toWeekStatsInfo(week.getStats())
                        : null;

        return new LearningPathDetailResponse.WeekInfo(
                week.getWeekNo(),
                week.getStatus().name(),
                week.getOpenedAt(),
                week.getCloseAt(),
                week.getClosedAt(),
                toProgressInfo(week.getProgress()),
                stats,
                days
        );
    }

    private LearningPathDetailResponse.DayInfo toDayInfo(
            LearningPath path,
            LearningPathDay day
    ) {
        LearningPathDayResult result = day.getResult();

        LearningPathDetailResponse.DayResultInfo resultInfo =
                result == null
                        ? null
                        : new LearningPathDetailResponse.DayResultInfo(
                        result.getCompletionRate(),
                        result.getPracticeScore()
                );

        return new LearningPathDetailResponse.DayInfo(
                day.getDayNo(),
                LearningPathDayStatus.effective(day.getStatus(), day.getCloseReason()).name(),
                day.getStartedDate(),
                day.getAutoCloseAt(),
                day.getClosedAt(),
                toProgressInfo(day.getProgress()),
                toSkillAllocations(path, day),
                resultInfo
        );
    }

    private List<LearningPathDetailResponse.SkillAllocationInfo> toSkillAllocations(
            LearningPath path,
            LearningPathDay day
    ) {
        List<LearningPathMicroSkillSnapshot> selectedSkills =
                path.getMicroSkills() == null
                        ? List.of()
                        : path.getMicroSkills();

        List<LearningPathDayItem> items = day.getItems() == null
                ? List.of()
                : day.getItems();

        return selectedSkills.stream()
                .map(skill -> {
                    int assigned = (int) items.stream()
                            .filter(item -> item.getMicroSkill() != null
                                    && item.getMicroSkill().equals(skill.getMicroSkill()))
                            .count();

                    return new LearningPathDetailResponse.SkillAllocationInfo(
                            skill.getMicroSkill(),
                            skill.getSkill(),
                            assigned
                    );
                })
                .toList();
    }

    private LearningPathDetailResponse.WeekStatsInfo toWeekStatsInfo(
            LearningPathWeekStats stats
    ) {
        List<LearningPathDetailResponse.MicroSkillWeekStatsInfo> byMicroSkill =
                (stats.getByMicroSkill() == null
                        ? Collections.<LearningPathMicroSkillWeekStats>emptyList()
                        : stats.getByMicroSkill())
                        .stream()
                        .map(skill ->
                                new LearningPathDetailResponse.MicroSkillWeekStatsInfo(
                                        skill.getMicroSkill(),
                                        skill.getAssigned(),
                                        skill.getAnswered(),
                                        skill.getCompletionRate(),
                                        skill.getPracticeScore(),
                                        toComparedToInfo(skill.getComparedTo()),
                                        skill.getImprovementPp()
                                )
                        )
                        .toList();

        LearningPathOverallWeekStats overall = stats.getOverall();

        LearningPathDetailResponse.OverallWeekStatsInfo overallInfo =
                overall == null
                        ? null
                        : new LearningPathDetailResponse.OverallWeekStatsInfo(
                        overall.getAssigned(),
                        overall.getAnswered(),
                        overall.getCorrect(),
                        calculateWrong(
                                overall.getAnswered(),
                                overall.getCorrect()
                        ),
                        overall.getCompletionRate(),
                        overall.getPracticeScore()
                );

        return new LearningPathDetailResponse.WeekStatsInfo(
                byMicroSkill,
                overallInfo
        );
    }

    private LearningPathDetailResponse.ComparedToInfo toComparedToInfo(
            LearningPathComparedTo comparedTo
    ) {
        if (comparedTo == null) {
            return null;
        }

        return new LearningPathDetailResponse.ComparedToInfo(
                comparedTo.getType().name(),
                comparedTo.getWeekNo(),
                comparedTo.getPercent()
        );
    }

    private LearningPathDetailResponse.SummaryInfo toSummaryInfo(
            LearningPathSummary summary
    ) {
        List<LearningPathDetailResponse.SummaryMicroSkillInfo> byMicroSkill =
                (summary.getByMicroSkill() == null
                        ? Collections.<LearningPathSummaryMicroSkill>emptyList()
                        : summary.getByMicroSkill())
                        .stream()
                        .map(skill ->
                                new LearningPathDetailResponse.SummaryMicroSkillInfo(
                                        skill.getMicroSkill(),
                                        skill.getBaselinePercent(),
                                        skill.getAnswered(),
                                        skill.getCorrect(),
                                        skill.getFinalPracticeScore(),
                                        skill.getImprovementPp()
                                )
                        )
                        .toList();

        LearningPathSummaryCompletion completion = summary.getCompletion();

        LearningPathDetailResponse.SummaryCompletionInfo completionInfo =
                completion == null
                        ? null
                        : new LearningPathDetailResponse.SummaryCompletionInfo(
                        completion.getAssigned(),
                        completion.getAnswered(),
                        completion.getCompletionRate()
                );

        return new LearningPathDetailResponse.SummaryInfo(
                byMicroSkill,
                completionInfo,
                summary.getComputedAt()
        );
    }

    private LearningPathDetailResponse.ProgressInfo toProgressInfo(
            LearningPathProgress progress
    ) {
        if (progress == null) {
            return null;
        }

        return new LearningPathDetailResponse.ProgressInfo(
                progress.getAssigned(),
                progress.getAnswered()
        );
    }

    private Integer calculateWrong(Integer answered, Integer correct) {
        if (answered == null || correct == null) {
            return null;
        }

        return Math.max(0, answered - correct);
    }
}
