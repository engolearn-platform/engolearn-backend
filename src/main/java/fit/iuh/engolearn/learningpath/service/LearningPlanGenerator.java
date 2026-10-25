package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import org.bson.Document;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class LearningPlanGenerator {

    private static final ZoneOffset LEARNING_ZONE = ZoneOffset.ofHours(7);

    public List<LearningPath.Week> generate(List<String> skills,
                                            Map<String, List<Document>> selected,
                                            Date createdAt,
                                            LocalDate createdDate) {
        int k = skills.size();
        if (k < 1 || k > 5) {
            throw new IllegalArgumentException("Learning Path needs 1 to 5 micro-skills");
        }
        Set<String> questionIds = new HashSet<>();
        for (String skill : skills) {
            List<Document> questions = selected.get(skill);
            if (questions == null || questions.size() != 30) {
                throw new IllegalStateException("Question Pool has fewer than 30 selected questions");
            }
            for (Document question : questions) {
                String questionId = question.getString("question_id");
                if (questionId == null || !skill.equals(question.getString("micro_skill"))
                        || !questionIds.add(questionId)) {
                    throw new IllegalStateException("Question Pool contains duplicate or mismatched questions");
                }
            }
        }

        List<LearningPath.Week> weeks = new ArrayList<>(3);
        for (int weekIndex = 0; weekIndex < 3; weekIndex++) {
            List<List<LearningPath.DayItem>> dayItems = new ArrayList<>(k);
            for (int dayIndex = 0; dayIndex < k; dayIndex++) {
                dayItems.add(new ArrayList<>(10));
            }
            for (int skillIndex = 0; skillIndex < k; skillIndex++) {
                String skill = skills.get(skillIndex);
                List<Document> questions = selected.get(skill);
                int cursor = weekIndex * 10;
                for (int dayIndex = 0; dayIndex < k; dayIndex++) {
                    int extraPosition = Math.floorMod(dayIndex - skillIndex - weekIndex, k);
                    int count = 10 / k + (extraPosition < 10 % k ? 1 : 0);
                    for (int offset = 0; offset < count; offset++) {
                        LearningPath.DayItem item = new LearningPath.DayItem();
                        item.setQuestionId(questions.get(cursor++).getString("question_id"));
                        item.setMicroSkill(skill);
                        dayItems.get(dayIndex).add(item);
                    }
                }
                if (cursor != (weekIndex + 1) * 10) {
                    throw new IllegalStateException("Question allocation is incomplete");
                }
            }

            List<LearningPath.Day> days = new ArrayList<>(k);
            for (int dayIndex = 0; dayIndex < k; dayIndex++) {
                List<LearningPath.DayItem> items = dayItems.get(dayIndex);
                if (items.size() != 10) {
                    throw new IllegalStateException("Each learning day must contain 10 questions");
                }
                Collections.shuffle(items);
                for (int order = 0; order < items.size(); order++) {
                    items.get(order).setOrder(order + 1);
                }
                LearningPath.Day day = new LearningPath.Day();
                day.setDayNo(dayIndex + 1);
                day.setStatus("NOT_STARTED");
                day.setProgress(progress(10));
                day.setItems(items);
                days.add(day);
            }

            LearningPath.Week week = new LearningPath.Week();
            week.setWeekNo(weekIndex + 1);
            week.setStatus(weekIndex == 0 ? "OPEN" : "LOCKED");
            if (weekIndex == 0) {
                week.setOpenedAt(createdAt);
                week.setCloseAt(Date.from(createdDate.plusDays(7)
                        .atStartOfDay(LEARNING_ZONE).toInstant()));
            }
            week.setProgress(progress(k * 10));
            week.setDays(days);
            weeks.add(week);
        }
        return weeks;
    }

    private LearningPath.Progress progress(int assigned) {
        LearningPath.Progress progress = new LearningPath.Progress();
        progress.setAssigned(assigned);
        progress.setAnswered(0);
        progress.setCorrect(0);
        return progress;
    }
}
