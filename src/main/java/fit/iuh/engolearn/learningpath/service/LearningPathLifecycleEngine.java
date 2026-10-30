package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathCancelReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathComparisonType;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekCloseReason;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathAnswer;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathCancel;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathComparedTo;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LearningPathLifecycleEngine {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    Map<String, Object> advance(LearningPath path, Instant now) {
        return advance(path, now, Map.of());
    }

    Map<String, Object> submit(LearningPath path, int weekIndex, int dayIndex,
                               Instant now, Map<String, String> answerKeys) {
        Map<String, Object> changes = new LinkedHashMap<>();
        LearningPathWeek week = path.getWeeks().get(weekIndex);
        LearningPathDay day = week.getDays().get(dayIndex);
        boolean complete = day.getItems().stream().allMatch(item -> item.getDraft() != null || item.getAnswer() != null);
        closeDay(path, week, day, dayIndex, weekIndex, now,
                complete ? LearningPathDayCloseReason.ALL_ANSWERED : LearningPathDayCloseReason.SUBMITTED,
                answerKeys, changes);
        LearningPathDayStatus submittedStatus = complete ? LearningPathDayStatus.COMPLETED : LearningPathDayStatus.SUBMITTED;
        day.setStatus(submittedStatus);
        changes.put("weeks." + weekIndex + ".days." + dayIndex + ".status", submittedStatus.name());
        if (week.getWeekNo() == 3 && week.getDays().stream().allMatch(d ->
                d.getItems() != null && !d.getItems().isEmpty()
                        && d.getItems().stream().allMatch(item -> item.getAnswer() != null))) {
            closeWeek(path, week, weekIndex, now, answerKeys, changes, LearningPathWeekCloseReason.ALL_ANSWERED);
        }
        Instant next = Boolean.TRUE.equals(path.getActive()) ? nextEvent(path, openWeek(path)) : null;
        changes.put("next_run_at", next == null ? null : Date.from(next));
        return changes;
    }

    Map<String, Object> advance(LearningPath path, Instant now,
                                Map<String, String> answerKeys) {
        Map<String, Object> changes = new LinkedHashMap<>();
        LearningPathWeek week = openWeek(path);
        Instant eventAt = nextEvent(path, week);
        if (eventAt == null) {
            throw new IllegalStateException("Active Learning Path has no scheduled deadline: " + path.getId());
        }
        if (eventAt.isAfter(now)) {
            changes.put("next_run_at", Date.from(eventAt));
            return changes;
        }

        int weekIndex = path.getWeeks().indexOf(week);
        for (int dayIndex = 0; dayIndex < week.getDays().size(); dayIndex++) {
            LearningPathDay day = week.getDays().get(dayIndex);
            if (LearningPathDayStatus.IN_PROGRESS == day.getStatus() && day.getAutoCloseAt() != null
                    && !day.getAutoCloseAt().toInstant().isAfter(eventAt)) {
                closeDay(path, week, day, dayIndex, weekIndex,
                        day.getAutoCloseAt().toInstant(), LearningPathDayCloseReason.MIDNIGHT, answerKeys, changes);
            }
        }

        Instant weekDue = weekDeadline(week);
        if (weekDue != null && !weekDue.isAfter(eventAt)) {
            closeWeek(path, week, weekIndex, eventAt, answerKeys, changes);
        }

        Instant cancelAt = cancelDeadline(path);
        if (Boolean.TRUE.equals(path.getActive()) && cancelAt != null
                && !cancelAt.isAfter(eventAt)) {
            LearningPathCancel cancel = new LearningPathCancel();
            cancel.setAt(Date.from(cancelAt));
            cancel.setReason(LearningPathCancelReason.INACTIVITY);
            path.setCancel(cancel);
            path.setStatus(LearningPathStatus.CANCELLED);
            path.setActive(false);
            path.setEndedAt(Date.from(cancelAt));
            changes.put("cancel", cancel);
            changes.put("status", "CANCELLED");
            changes.put("is_active", false);
            changes.put("ended_at", Date.from(cancelAt));
        }

        Instant next = Boolean.TRUE.equals(path.getActive())
                ? nextEvent(path, openWeek(path)) : null;
        changes.put("next_run_at", next == null ? null : Date.from(next));
        return changes;
    }

    private LearningPathWeek openWeek(LearningPath path) {
        if (path.getWeeks() == null) {
            throw new IllegalStateException("Learning Path has no weeks: " + path.getId());
        }
        return path.getWeeks().stream()
                .filter(week -> LearningPathWeekStatus.OPEN == week.getStatus())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Active Learning Path has no open week: " + path.getId()));
    }

    private Instant nextEvent(LearningPath path, LearningPathWeek week) {
        Instant next = weekDeadline(week);
        for (LearningPathDay day : week.getDays()) {
            if (LearningPathDayStatus.IN_PROGRESS == day.getStatus() && day.getAutoCloseAt() != null) {
                next = earlier(next, day.getAutoCloseAt().toInstant());
            }
        }
        return earlier(next, cancelDeadline(path));
    }

    private Instant weekDeadline(LearningPathWeek week) {
        Instant deadline = week.getCloseAt() == null ? null : week.getCloseAt().toInstant();
        if (week.getWeekNo() != null && week.getWeekNo() < 3 && week.getDays() != null
                && !week.getDays().isEmpty()
                && week.getDays().stream().allMatch(this::isTerminalDay)) {
            Instant lastClosed = week.getDays().stream()
                    .map(LearningPathDay::getClosedAt)
                    .filter(date -> date != null)
                    .map(Date::toInstant)
                    .max(Instant::compareTo)
                    .orElse(null);
            if (lastClosed != null) {
                deadline = earlier(deadline, midnightAtOrAfter(lastClosed));
            }
        }
        return deadline;
    }

    private boolean isTerminalDay(LearningPathDay day) {
        return LearningPathDayStatus.COMPLETED == day.getStatus()
                || LearningPathDayStatus.SUBMITTED == day.getStatus()
                || LearningPathDayStatus.CLOSED == day.getStatus();
    }

    private Instant midnightAtOrAfter(Instant instant) {
        ZonedDateTime local = instant.atZone(ZONE);
        Instant midnight = local.toLocalDate().atStartOfDay(ZONE).toInstant();
        return midnight.equals(instant) ? midnight
                : local.toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    private Instant cancelDeadline(LearningPath path) {
        if (path.getInactivity() != null && path.getInactivity().getCancelAt() != null) {
            return path.getInactivity().getCancelAt().toInstant();
        }
        String anchor = path.getInactivity() != null
                && path.getInactivity().getAnchorDate() != null
                ? path.getInactivity().getAnchorDate() : path.getCreatedDate();
        return anchor == null ? null
                : LocalDate.parse(anchor).plusDays(15).atStartOfDay(ZONE).toInstant();
    }

    private Instant earlier(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }

    private void closeDay(LearningPath path, LearningPathWeek week, LearningPathDay day,
                          int dayIndex, int weekIndex, Instant at, LearningPathDayCloseReason reason,
                          Map<String, String> answerKeys, Map<String, Object> changes) {
        String prefix = "weeks." + weekIndex + ".days." + dayIndex + ".";
        if (day.getItems() != null) {
            for (int itemIndex = 0; itemIndex < day.getItems().size(); itemIndex++) {
                LearningPathDayItem item = day.getItems().get(itemIndex);
                if (item.getDraft() == null || item.getAnswer() != null) continue;
                String key = answerKeys.get(item.getQuestionId());
                if (key == null) {
                    throw new IllegalStateException("Missing answer key for " + item.getQuestionId());
                }
                LearningPathAnswer answer = new LearningPathAnswer();
                answer.setSelectedOptionId(item.getDraft().getSelectedOptionId());
                answer.setCorrect(key.equals(item.getDraft().getSelectedOptionId()));
                answer.setAnsweredAt(item.getDraft().getSavedAt());
                item.setAnswer(answer);
                item.setDraft(null);
                String itemPrefix = prefix + "items." + itemIndex + ".";
                changes.put(itemPrefix + "answer", answer);
                changes.put(itemPrefix + "draft", null);
            }
        }
        syncProgress(path, week, day, weekIndex, prefix, changes);
        LearningPathDayResult result = dayResult(day);
        day.setStatus(LearningPathDayStatus.CLOSED);
        day.setClosedAt(Date.from(at));
        day.setCloseReason(reason);
        day.setResult(result);
        changes.put(prefix + "status", "CLOSED");
        changes.put(prefix + "closed_at", Date.from(at));
        changes.put(prefix + "close_reason", reason.name());
        changes.put(prefix + "result", result);
    }

    private void syncProgress(LearningPath path, LearningPathWeek week,
                              LearningPathDay day, int weekIndex, String dayPrefix,
                              Map<String, Object> changes) {
        Counts dayCounts = countItems(day.getItems(), null);
        if (day.getProgress() == null) day.setProgress(new LearningPathProgress());
        updateProgress(day.getProgress(), dayCounts);
        changes.put(dayPrefix + "progress", day.getProgress());
        Counts weekCounts = countDays(week.getDays(), null);
        if (week.getProgress() == null) week.setProgress(new LearningPathProgress());
        updateProgress(week.getProgress(), weekCounts);
        changes.put("weeks." + weekIndex + ".progress", week.getProgress());
        Counts pathCounts = new Counts();
        for (LearningPathWeek current : path.getWeeks()) {
            Counts counts = countDays(current.getDays(), null);
            pathCounts.assigned += counts.assigned;
            pathCounts.answered += counts.answered;
            pathCounts.correct += counts.correct;
        }
        if (path.getProgress() == null) path.setProgress(new LearningPathProgress());
        updateProgress(path.getProgress(), pathCounts);
        changes.put("progress", path.getProgress());
    }

    private void updateProgress(LearningPathProgress progress, Counts counts) {
        progress.setAssigned(counts.assigned);
        progress.setAnswered(counts.answered);
        progress.setCorrect(counts.correct);
    }

    private LearningPathDayResult dayResult(LearningPathDay day) {
        Counts counts = countItems(day.getItems(), null);
        LearningPathDayResult result = new LearningPathDayResult();
        result.setWrong(counts.answered - counts.correct);
        result.setCompletionRate(rate(counts.answered, counts.assigned));
        result.setPracticeScore(rate(counts.correct, counts.answered));
        return result;
    }

    private void closeWeek(LearningPath path, LearningPathWeek week, int weekIndex,
                           Instant at, Map<String, String> answerKeys,
                           Map<String, Object> changes) {
        closeWeek(path, week, weekIndex, at, answerKeys, changes, null);
    }

    private void closeWeek(LearningPath path, LearningPathWeek week, int weekIndex,
                           Instant at, Map<String, String> answerKeys,
                           Map<String, Object> changes, LearningPathWeekCloseReason forcedReason) {
        for (int dayIndex = 0; dayIndex < week.getDays().size(); dayIndex++) {
            LearningPathDay day = week.getDays().get(dayIndex);
            if (!isTerminalDay(day)) {
                closeDay(path, week, day, dayIndex, weekIndex, at,
                        LearningPathDayStatus.NOT_STARTED == day.getStatus()
                                ? LearningPathDayCloseReason.WEEK_CLOSED : LearningPathDayCloseReason.MIDNIGHT,
                        answerKeys, changes);
            }
        }

        String prefix = "weeks." + weekIndex + ".";
        LearningPathWeekCloseReason reason = forcedReason != null ? forcedReason : week.getCloseAt() != null && !week.getCloseAt().toInstant().isAfter(at)
                ? LearningPathWeekCloseReason.TIME_UP : LearningPathWeekCloseReason.ALL_DAYS_DONE;
        LearningPathWeekStats stats = weekStats(path, week, weekIndex);
        week.setStatus(LearningPathWeekStatus.CLOSED);
        week.setClosedAt(Date.from(at));
        week.setCloseAt(Date.from(at));
        week.setCloseReason(reason);
        week.setStats(stats);
        changes.put(prefix + "status", "CLOSED");
        changes.put(prefix + "closed_at", Date.from(at));
        changes.put(prefix + "close_at", Date.from(at));
        changes.put(prefix + "close_reason", reason.name());
        changes.put(prefix + "stats", stats);

        if (week.getWeekNo() == 3) {
            LearningPathSummary summary = summary(path, at);
            path.setSummary(summary);
            path.setStatus(LearningPathStatus.COMPLETED);
            path.setActive(false);
            path.setEndedAt(Date.from(at));
            changes.put("summary", summary);
            changes.put("status", "COMPLETED");
            changes.put("is_active", false);
            changes.put("ended_at", Date.from(at));
            return;
        }

        LearningPathWeek next = path.getWeeks().get(weekIndex + 1);
        Instant nextClose = at.atZone(ZONE).toLocalDate().plusDays(7)
                .atStartOfDay(ZONE).toInstant();
        next.setStatus(LearningPathWeekStatus.OPEN);
        next.setOpenedAt(Date.from(at));
        next.setCloseAt(Date.from(nextClose));
        path.setCurrentWeekNo(next.getWeekNo());
        String nextPrefix = "weeks." + (weekIndex + 1) + ".";
        changes.put(nextPrefix + "status", "OPEN");
        changes.put(nextPrefix + "opened_at", Date.from(at));
        changes.put(nextPrefix + "close_at", Date.from(nextClose));
        changes.put("current_week_no", next.getWeekNo());
    }

    private LearningPathWeekStats weekStats(LearningPath path, LearningPathWeek week,
                                             int weekIndex) {
        LearningPathWeekStats stats = new LearningPathWeekStats();
        List<LearningPathMicroSkillWeekStats> bySkill = new ArrayList<>();
        for (LearningPathMicroSkillSnapshot skill : path.getMicroSkills()) {
            Counts counts = countDays(week.getDays(), skill.getMicroSkill());
            Double practiceScore = rate(counts.correct, counts.answered);
            Double previous = weekIndex == 0
                    ? skill.getBaseline().getPercent()
                    : previousScore(path.getWeeks().get(weekIndex - 1), skill.getMicroSkill());
            LearningPathMicroSkillWeekStats entry = new LearningPathMicroSkillWeekStats();
            entry.setMicroSkill(skill.getMicroSkill());
            entry.setAssigned(counts.assigned);
            entry.setAnswered(counts.answered);
            entry.setCorrect(counts.correct);
            entry.setCompletionRate(rate(counts.answered, counts.assigned));
            entry.setPracticeScore(practiceScore);
            entry.setComparedTo(comparison(weekIndex, previous));
            entry.setImprovementPp(difference(practiceScore, previous));
            bySkill.add(entry);
        }
        stats.setByMicroSkill(bySkill);

        Counts total = countDays(week.getDays(), null);
        Double previous = weekIndex == 0
                ? path.getBaselineTotal().getPercent()
                : path.getWeeks().get(weekIndex - 1).getStats().getOverall().getPracticeScore();
        Double practiceScore = rate(total.correct, total.answered);
        LearningPathOverallWeekStats overall = new LearningPathOverallWeekStats();
        overall.setAssigned(total.assigned);
        overall.setAnswered(total.answered);
        overall.setCorrect(total.correct);
        overall.setCompletionRate(rate(total.answered, total.assigned));
        overall.setPracticeScore(practiceScore);
        overall.setComparedTo(comparison(weekIndex, previous));
        overall.setImprovementPp(difference(practiceScore, previous));
        stats.setOverall(overall);
        return stats;
    }

    private LearningPathComparedTo comparison(int weekIndex, Double percent) {
        LearningPathComparedTo comparedTo = new LearningPathComparedTo();
        comparedTo.setType(weekIndex == 0
                ? LearningPathComparisonType.SOURCE_TEST : LearningPathComparisonType.PREVIOUS_WEEK);
        comparedTo.setWeekNo(weekIndex == 0 ? null : weekIndex);
        comparedTo.setPercent(percent);
        return comparedTo;
    }

    private Double previousScore(LearningPathWeek previousWeek, String microSkill) {
        return previousWeek.getStats().getByMicroSkill().stream()
                .filter(entry -> microSkill.equals(entry.getMicroSkill()))
                .findFirst()
                .map(LearningPathMicroSkillWeekStats::getPracticeScore)
                .orElse(null);
    }

    private LearningPathSummary summary(LearningPath path, Instant at) {
        LearningPathSummary summary = new LearningPathSummary();
        List<LearningPathSummaryMicroSkill> bySkill = new ArrayList<>();
        List<LearningPathDay> allDays = path.getWeeks().stream()
                .flatMap(week -> week.getDays().stream()).toList();
        for (LearningPathMicroSkillSnapshot skill : path.getMicroSkills()) {
            Counts counts = countDays(allDays, skill.getMicroSkill());
            Double score = rate(counts.correct, counts.answered);
            Double baseline = skill.getBaseline().getPercent();
            LearningPathSummaryMicroSkill entry = new LearningPathSummaryMicroSkill();
            entry.setMicroSkill(skill.getMicroSkill());
            entry.setBaselinePercent(baseline);
            entry.setAnswered(counts.answered);
            entry.setCorrect(counts.correct);
            entry.setFinalPracticeScore(score);
            entry.setImprovementPp(difference(score, baseline));
            bySkill.add(entry);
        }
        Counts total = countDays(allDays, null);
        LearningPathSummaryCompletion completion = new LearningPathSummaryCompletion();
        completion.setAssigned(total.assigned);
        completion.setAnswered(total.answered);
        completion.setCompletionRate(rate(total.answered, total.assigned));
        summary.setByMicroSkill(bySkill);
        summary.setCompletion(completion);
        summary.setComputedAt(Date.from(at));
        return summary;
    }

    private Counts countDays(List<LearningPathDay> days, String microSkill) {
        Counts total = new Counts();
        for (LearningPathDay day : days) {
            Counts current = countItems(day.getItems(), microSkill);
            total.assigned += current.assigned;
            total.answered += current.answered;
            total.correct += current.correct;
        }
        return total;
    }

    private Counts countItems(List<LearningPathDayItem> items, String microSkill) {
        Counts counts = new Counts();
        if (items == null) return counts;
        for (LearningPathDayItem item : items) {
            if (microSkill != null && !microSkill.equals(item.getMicroSkill())) continue;
            counts.assigned++;
            if (item.getAnswer() != null) {
                counts.answered++;
                if (Boolean.TRUE.equals(item.getAnswer().getCorrect())) counts.correct++;
            }
        }
        return counts;
    }

    private Double rate(int numerator, int denominator) {
        return denominator == 0 ? null : 100.0 * numerator / denominator;
    }

    private Double difference(Double current, Double baseline) {
        return current == null || baseline == null ? null : current - baseline;
    }

    private static final class Counts {
        private int assigned;
        private int answered;
        private int correct;
    }
}
