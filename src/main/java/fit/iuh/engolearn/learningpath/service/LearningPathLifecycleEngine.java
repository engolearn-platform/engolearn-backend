package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;

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
        Map<String, Object> changes = new LinkedHashMap<>();
        LearningPath.Week week = openWeek(path);
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
            LearningPath.Day day = week.getDays().get(dayIndex);
            if ("IN_PROGRESS".equals(day.getStatus()) && day.getAutoCloseAt() != null
                    && !day.getAutoCloseAt().toInstant().isAfter(eventAt)) {
                closeDay(day, dayIndex, weekIndex, day.getAutoCloseAt().toInstant(),
                        "MIDNIGHT", changes);
            }
        }

        Instant weekDue = weekDeadline(week);
        if (weekDue != null && !weekDue.isAfter(eventAt)) {
            closeWeek(path, week, weekIndex, eventAt, changes);
        }

        Instant cancelAt = cancelDeadline(path);
        if (Boolean.TRUE.equals(path.getActive()) && cancelAt != null
                && !cancelAt.isAfter(eventAt)) {
            LearningPath.Cancel cancel = new LearningPath.Cancel();
            cancel.setAt(Date.from(cancelAt));
            cancel.setReason("INACTIVITY");
            path.setCancel(cancel);
            path.setStatus("CANCELLED");
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

    private LearningPath.Week openWeek(LearningPath path) {
        if (path.getWeeks() == null) {
            throw new IllegalStateException("Learning Path has no weeks: " + path.getId());
        }
        return path.getWeeks().stream()
                .filter(week -> "OPEN".equals(week.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Active Learning Path has no open week: " + path.getId()));
    }

    private Instant nextEvent(LearningPath path, LearningPath.Week week) {
        Instant next = weekDeadline(week);
        for (LearningPath.Day day : week.getDays()) {
            if ("IN_PROGRESS".equals(day.getStatus()) && day.getAutoCloseAt() != null) {
                next = earlier(next, day.getAutoCloseAt().toInstant());
            }
        }
        return earlier(next, cancelDeadline(path));
    }

    private Instant weekDeadline(LearningPath.Week week) {
        Instant deadline = week.getCloseAt() == null ? null : week.getCloseAt().toInstant();
        if (week.getWeekNo() != null && week.getWeekNo() < 3 && week.getDays() != null
                && !week.getDays().isEmpty()
                && week.getDays().stream().allMatch(this::isTerminalDay)) {
            Instant lastClosed = week.getDays().stream()
                    .map(LearningPath.Day::getClosedAt)
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

    private boolean isTerminalDay(LearningPath.Day day) {
        return "COMPLETED".equals(day.getStatus()) || "CLOSED".equals(day.getStatus());
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

    private void closeDay(LearningPath.Day day, int dayIndex, int weekIndex,
                          Instant at, String reason, Map<String, Object> changes) {
        String prefix = "weeks." + weekIndex + ".days." + dayIndex + ".";
        LearningPath.DayResult result = dayResult(day);
        day.setStatus("CLOSED");
        day.setClosedAt(Date.from(at));
        day.setCloseReason(reason);
        day.setResult(result);
        changes.put(prefix + "status", "CLOSED");
        changes.put(prefix + "closed_at", Date.from(at));
        changes.put(prefix + "close_reason", reason);
        changes.put(prefix + "result", result);
    }

    private LearningPath.DayResult dayResult(LearningPath.Day day) {
        Counts counts = countItems(day.getItems(), null);
        LearningPath.DayResult result = new LearningPath.DayResult();
        result.setWrong(counts.answered - counts.correct);
        result.setCompletionRate(rate(counts.answered, counts.assigned));
        result.setPracticeScore(rate(counts.correct, counts.answered));
        return result;
    }

    private void closeWeek(LearningPath path, LearningPath.Week week, int weekIndex,
                           Instant at, Map<String, Object> changes) {
        for (int dayIndex = 0; dayIndex < week.getDays().size(); dayIndex++) {
            LearningPath.Day day = week.getDays().get(dayIndex);
            if (!isTerminalDay(day)) {
                closeDay(day, dayIndex, weekIndex, at,
                        "NOT_STARTED".equals(day.getStatus()) ? "WEEK_CLOSED" : "MIDNIGHT",
                        changes);
            }
        }

        String prefix = "weeks." + weekIndex + ".";
        String reason = week.getCloseAt() != null && !week.getCloseAt().toInstant().isAfter(at)
                ? "TIME_UP" : "ALL_DAYS_DONE";
        LearningPath.WeekStats stats = weekStats(path, week, weekIndex);
        week.setStatus("CLOSED");
        week.setClosedAt(Date.from(at));
        week.setCloseAt(Date.from(at));
        week.setCloseReason(reason);
        week.setStats(stats);
        changes.put(prefix + "status", "CLOSED");
        changes.put(prefix + "closed_at", Date.from(at));
        changes.put(prefix + "close_at", Date.from(at));
        changes.put(prefix + "close_reason", reason);
        changes.put(prefix + "stats", stats);

        if (week.getWeekNo() == 3) {
            LearningPath.Summary summary = summary(path, at);
            path.setSummary(summary);
            path.setStatus("COMPLETED");
            path.setActive(false);
            path.setEndedAt(Date.from(at));
            changes.put("summary", summary);
            changes.put("status", "COMPLETED");
            changes.put("is_active", false);
            changes.put("ended_at", Date.from(at));
            return;
        }

        LearningPath.Week next = path.getWeeks().get(weekIndex + 1);
        Instant nextClose = at.atZone(ZONE).toLocalDate().plusDays(7)
                .atStartOfDay(ZONE).toInstant();
        next.setStatus("OPEN");
        next.setOpenedAt(Date.from(at));
        next.setCloseAt(Date.from(nextClose));
        path.setCurrentWeekNo(next.getWeekNo());
        String nextPrefix = "weeks." + (weekIndex + 1) + ".";
        changes.put(nextPrefix + "status", "OPEN");
        changes.put(nextPrefix + "opened_at", Date.from(at));
        changes.put(nextPrefix + "close_at", Date.from(nextClose));
        changes.put("current_week_no", next.getWeekNo());
    }

    private LearningPath.WeekStats weekStats(LearningPath path, LearningPath.Week week,
                                             int weekIndex) {
        LearningPath.WeekStats stats = new LearningPath.WeekStats();
        List<LearningPath.MicroSkillWeekStats> bySkill = new ArrayList<>();
        for (LearningPath.MicroSkillSnapshot skill : path.getMicroSkills()) {
            Counts counts = countDays(week.getDays(), skill.getMicroSkill());
            Double practiceScore = rate(counts.correct, counts.answered);
            Double previous = weekIndex == 0
                    ? skill.getBaseline().getPercent()
                    : previousScore(path.getWeeks().get(weekIndex - 1), skill.getMicroSkill());
            LearningPath.MicroSkillWeekStats entry = new LearningPath.MicroSkillWeekStats();
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
        LearningPath.OverallWeekStats overall = new LearningPath.OverallWeekStats();
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

    private LearningPath.ComparedTo comparison(int weekIndex, Double percent) {
        LearningPath.ComparedTo comparedTo = new LearningPath.ComparedTo();
        comparedTo.setType(weekIndex == 0 ? "SOURCE_TEST" : "PREVIOUS_WEEK");
        comparedTo.setWeekNo(weekIndex == 0 ? null : weekIndex);
        comparedTo.setPercent(percent);
        return comparedTo;
    }

    private Double previousScore(LearningPath.Week previousWeek, String microSkill) {
        return previousWeek.getStats().getByMicroSkill().stream()
                .filter(entry -> microSkill.equals(entry.getMicroSkill()))
                .findFirst()
                .map(LearningPath.MicroSkillWeekStats::getPracticeScore)
                .orElse(null);
    }

    private LearningPath.Summary summary(LearningPath path, Instant at) {
        LearningPath.Summary summary = new LearningPath.Summary();
        List<LearningPath.SummaryMicroSkill> bySkill = new ArrayList<>();
        List<LearningPath.Day> allDays = path.getWeeks().stream()
                .flatMap(week -> week.getDays().stream()).toList();
        for (LearningPath.MicroSkillSnapshot skill : path.getMicroSkills()) {
            Counts counts = countDays(allDays, skill.getMicroSkill());
            Double score = rate(counts.correct, counts.answered);
            Double baseline = skill.getBaseline().getPercent();
            LearningPath.SummaryMicroSkill entry = new LearningPath.SummaryMicroSkill();
            entry.setMicroSkill(skill.getMicroSkill());
            entry.setBaselinePercent(baseline);
            entry.setAnswered(counts.answered);
            entry.setCorrect(counts.correct);
            entry.setFinalPracticeScore(score);
            entry.setImprovementPp(difference(score, baseline));
            bySkill.add(entry);
        }
        Counts total = countDays(allDays, null);
        LearningPath.SummaryCompletion completion = new LearningPath.SummaryCompletion();
        completion.setAssigned(total.assigned);
        completion.setAnswered(total.answered);
        completion.setCompletionRate(rate(total.answered, total.assigned));
        summary.setByMicroSkill(bySkill);
        summary.setCompletion(completion);
        summary.setComputedAt(Date.from(at));
        return summary;
    }

    private Counts countDays(List<LearningPath.Day> days, String microSkill) {
        Counts total = new Counts();
        for (LearningPath.Day day : days) {
            Counts current = countItems(day.getItems(), microSkill);
            total.assigned += current.assigned;
            total.answered += current.answered;
            total.correct += current.correct;
        }
        return total;
    }

    private Counts countItems(List<LearningPath.DayItem> items, String microSkill) {
        Counts counts = new Counts();
        if (items == null) return counts;
        for (LearningPath.DayItem item : items) {
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
