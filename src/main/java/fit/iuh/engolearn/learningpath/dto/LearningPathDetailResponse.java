
package fit.iuh.engolearn.learningpath.dto;

import java.util.Date;
import java.util.List;

public record LearningPathDetailResponse(
        String id,
        String createdDate,
        String level,
        Integer k,
        String status,
        Integer currentWeekNo,
        ConfigInfo config,
        List<MicroSkillInfo> microSkills,
        ProgressInfo progress,
        List<WeekInfo> weeks,
        SummaryInfo summary
) {
    public record ConfigInfo(
            Integer durationWeeks,
            Integer questionsPerDay,
            Integer questionsPerSkillPerWeek
    ) {}

    public record MicroSkillInfo(
            String microSkill,
            String skill,
            BaselineInfo baseline
    ) {}

    public record BaselineInfo(Double percent) {}

    public record ProgressInfo(
            Integer assigned,
            Integer answered
    ) {}

    public record WeekInfo(
            Integer weekNo,
            String status,
            Date openedAt,
            Date closeAt,
            Date closedAt,
            ProgressInfo progress,
            WeekStatsInfo stats,
            List<DayInfo> days
    ) {}

    public record WeekStatsInfo(
            List<MicroSkillWeekStatsInfo> byMicroSkill,
            OverallWeekStatsInfo overall
    ) {}

    public record MicroSkillWeekStatsInfo(
            String microSkill,
            Integer assigned,
            Integer answered,
            Double completionRate,
            Double practiceScore,
            ComparedToInfo comparedTo,
            Double improvementPp
    ) {}

    public record OverallWeekStatsInfo(
            Integer assigned,
            Integer answered,
            Integer correct,
            Integer wrong,
            Double completionRate,
            Double practiceScore
    ) {}

    public record ComparedToInfo(
            String type,
            Integer weekNo,
            Double percent
    ) {}

    public record DayInfo(
            Integer dayNo,
            String status,
            String startedDate,
            Date autoCloseAt,
            Date closedAt,
            ProgressInfo progress,
            List<SkillAllocationInfo> skillAllocations,
            DayResultInfo result
    ) {}

    public record SkillAllocationInfo(
            String microSkill,
            String skill,
            Integer assigned
    ) {}

    public record DayResultInfo(
            Double completionRate,
            Double practiceScore
    ) {}

    public record SummaryInfo(
            List<SummaryMicroSkillInfo> byMicroSkill,
            SummaryCompletionInfo completion,
            Date computedAt
    ) {}

    public record SummaryMicroSkillInfo(
            String microSkill,
            Double baselinePercent,
            Integer answered,
            Integer correct,
            Double finalPracticeScore,
            Double improvementPp
    ) {}

    public record SummaryCompletionInfo(
            Integer assigned,
            Integer answered,
            Double completionRate
    ) {}
}
