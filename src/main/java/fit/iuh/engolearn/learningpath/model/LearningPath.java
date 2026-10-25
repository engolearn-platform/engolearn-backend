
package fit.iuh.engolearn.learningpath.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Document(collection = "learning_paths")
public class LearningPath {

    @Id
    private ObjectId id;

    @Field("user_id")
    private ObjectId userId;

    private Source source;

    @Field("created_at")
    private Date createdAt;

    @Field("created_date")
    private String createdDate;

    @Field("ended_at")
    private Date endedAt;

    private String level;
    private Integer k;
    private String status;

    @Field("is_active")
    private Boolean active;

    @Field("current_week_no")
    private Integer currentWeekNo;

    private Config config;

    @Field("micro_skills")
    private List<MicroSkillSnapshot> microSkills;

    @Field("baseline_total")
    private BaselineTotal baselineTotal;

    private Progress progress;

    @Field("next_run_at")
    private Date nextRunAt;

    private List<Week> weeks;
    private Summary summary;
    private Cancel cancel;

    public ObjectId getId() {
        return id;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public Source getSource() {
        return source;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public Date getEndedAt() {
        return endedAt;
    }

    public String getLevel() {
        return level;
    }

    public Integer getK() {
        return k;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getActive() {
        return active;
    }

    public Integer getCurrentWeekNo() {
        return currentWeekNo;
    }

    public Config getConfig() {
        return config;
    }

    public List<MicroSkillSnapshot> getMicroSkills() {
        return microSkills;
    }

    public BaselineTotal getBaselineTotal() {
        return baselineTotal;
    }

    public Progress getProgress() {
        return progress;
    }

    public Date getNextRunAt() {
        return nextRunAt;
    }

    public List<Week> getWeeks() {
        return weeks;
    }

    public Summary getSummary() {
        return summary;
    }

    public Cancel getCancel() {
        return cancel;
    }

    public static class Source {

        @Field("attempt_id")
        private ObjectId attemptId;

        public ObjectId getAttemptId() {
            return attemptId;
        }
    }

    public static class Config {

        @Field("duration_weeks")
        private Integer durationWeeks;

        @Field("questions_per_day")
        private Integer questionsPerDay;

        @Field("questions_per_skill_per_week")
        private Integer questionsPerSkillPerWeek;

        public Integer getDurationWeeks() {
            return durationWeeks;
        }

        public Integer getQuestionsPerDay() {
            return questionsPerDay;
        }

        public Integer getQuestionsPerSkillPerWeek() {
            return questionsPerSkillPerWeek;
        }
    }

    public static class MicroSkillSnapshot {

        @Field("micro_skill")
        private String microSkill;

        private String skill;
        private Baseline baseline;

        public String getMicroSkill() {
            return microSkill;
        }

        public String getSkill() {
            return skill;
        }

        public Baseline getBaseline() {
            return baseline;
        }
    }

    public static class Baseline {

        private Integer correct;
        private Integer total;
        private Double percent;
        private String band;

        @Field("result_id")
        private ObjectId resultId;

        public Integer getCorrect() {
            return correct;
        }

        public Integer getTotal() {
            return total;
        }

        public Double getPercent() {
            return percent;
        }

        public String getBand() {
            return band;
        }

        public ObjectId getResultId() {
            return resultId;
        }
    }

    public static class BaselineTotal {

        private Integer correct;
        private Integer total;
        private Double percent;

        public Integer getCorrect() {
            return correct;
        }

        public Integer getTotal() {
            return total;
        }

        public Double getPercent() {
            return percent;
        }
    }

    public static class Progress {

        private Integer assigned;
        private Integer answered;
        private Integer correct;

        public Integer getAssigned() {
            return assigned;
        }

        public Integer getAnswered() {
            return answered;
        }

        public Integer getCorrect() {
            return correct;
        }
    }

    public static class Week {

        @Field("week_no")
        private Integer weekNo;

        private String status;

        @Field("opened_at")
        private Date openedAt;

        @Field("close_at")
        private Date closeAt;

        @Field("closed_at")
        private Date closedAt;

        @Field("close_reason")
        private String closeReason;

        private Progress progress;
        private WeekStats stats;
        private List<Day> days;

        public Integer getWeekNo() {
            return weekNo;
        }

        public String getStatus() {
            return status;
        }

        public Date getOpenedAt() {
            return openedAt;
        }

        public Date getCloseAt() {
            return closeAt;
        }

        public Date getClosedAt() {
            return closedAt;
        }

        public String getCloseReason() {
            return closeReason;
        }

        public Progress getProgress() {
            return progress;
        }

        public WeekStats getStats() {
            return stats;
        }

        public List<Day> getDays() {
            return days;
        }
    }

    public static class WeekStats {

        @Field("by_micro_skill")
        private List<MicroSkillWeekStats> byMicroSkill;

        private OverallWeekStats overall;

        public List<MicroSkillWeekStats> getByMicroSkill() {
            return byMicroSkill;
        }

        public OverallWeekStats getOverall() {
            return overall;
        }
    }

    public static class MicroSkillWeekStats {

        @Field("micro_skill")
        private String microSkill;

        private Integer assigned;
        private Integer answered;
        private Integer correct;

        @Field("completion_rate")
        private Double completionRate;

        @Field("practice_score")
        private Double practiceScore;

        @Field("compared_to")
        private ComparedTo comparedTo;

        @Field("improvement_pp")
        private Double improvementPp;

        public String getMicroSkill() {
            return microSkill;
        }

        public Integer getAssigned() {
            return assigned;
        }

        public Integer getAnswered() {
            return answered;
        }

        public Integer getCorrect() {
            return correct;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public Double getPracticeScore() {
            return practiceScore;
        }

        public ComparedTo getComparedTo() {
            return comparedTo;
        }

        public Double getImprovementPp() {
            return improvementPp;
        }
    }

    public static class OverallWeekStats {

        private Integer assigned;
        private Integer answered;
        private Integer correct;

        @Field("completion_rate")
        private Double completionRate;

        @Field("practice_score")
        private Double practiceScore;

        public Integer getAssigned() {
            return assigned;
        }

        public Integer getAnswered() {
            return answered;
        }

        public Integer getCorrect() {
            return correct;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public Double getPracticeScore() {
            return practiceScore;
        }
    }

    public static class ComparedTo {

        private String type;

        @Field("week_no")
        private Integer weekNo;

        private Double percent;

        public String getType() {
            return type;
        }

        public Integer getWeekNo() {
            return weekNo;
        }

        public Double getPercent() {
            return percent;
        }
    }

    public static class Day {

        @Field("day_no")
        private Integer dayNo;

        private String status;

        @Field("started_at")
        private Date startedAt;

        @Field("started_date")
        private String startedDate;

        @Field("auto_close_at")
        private Date autoCloseAt;

        @Field("closed_at")
        private Date closedAt;

        @Field("close_reason")
        private String closeReason;

        private Progress progress;
        private DayResult result;
        private List<DayItem> items;

        public Integer getDayNo() {
            return dayNo;
        }

        public String getStatus() {
            return status;
        }

        public Date getStartedAt() {
            return startedAt;
        }

        public String getStartedDate() {
            return startedDate;
        }

        public Date getAutoCloseAt() {
            return autoCloseAt;
        }

        public Date getClosedAt() {
            return closedAt;
        }

        public String getCloseReason() {
            return closeReason;
        }

        public Progress getProgress() {
            return progress;
        }

        public DayResult getResult() {
            return result;
        }

        public List<DayItem> getItems() {
            return items;
        }
    }

    public static class DayItem {

        @Field("micro_skill")
        private String microSkill;

        public String getMicroSkill() {
            return microSkill;
        }
    }

    public static class DayResult {

        private Integer wrong;

        @Field("completion_rate")
        private Double completionRate;

        @Field("practice_score")
        private Double practiceScore;

        public Integer getWrong() {
            return wrong;
        }

        public Double getCompletionRate() {
            return completionRate;
        }

        public Double getPracticeScore() {
            return practiceScore;
        }
    }

    public static class Summary {

        @Field("by_micro_skill")
        private List<SummaryMicroSkill> byMicroSkill;

        private SummaryCompletion completion;

        @Field("computed_at")
        private Date computedAt;

        public List<SummaryMicroSkill> getByMicroSkill() {
            return byMicroSkill;
        }

        public SummaryCompletion getCompletion() {
            return completion;
        }

        public Date getComputedAt() {
            return computedAt;
        }
    }

    public static class SummaryMicroSkill {

        @Field("micro_skill")
        private String microSkill;

        @Field("baseline_percent")
        private Double baselinePercent;

        private Integer answered;
        private Integer correct;

        @Field("final_practice_score")
        private Double finalPracticeScore;

        @Field("improvement_pp")
        private Double improvementPp;

        public String getMicroSkill() {
            return microSkill;
        }

        public Double getBaselinePercent() {
            return baselinePercent;
        }

        public Integer getAnswered() {
            return answered;
        }

        public Integer getCorrect() {
            return correct;
        }

        public Double getFinalPracticeScore() {
            return finalPracticeScore;
        }

        public Double getImprovementPp() {
            return improvementPp;
        }
    }

    public static class SummaryCompletion {

        private Integer assigned;
        private Integer answered;

        @Field("completion_rate")
        private Double completionRate;

        public Integer getAssigned() {
            return assigned;
        }

        public Integer getAnswered() {
            return answered;
        }

        public Double getCompletionRate() {
            return completionRate;
        }
    }

    public static class Cancel {

        private Date at;
        private String reason;

        public Date getAt() {
            return at;
        }

        public String getReason() {
            return reason;
        }
    }
}
