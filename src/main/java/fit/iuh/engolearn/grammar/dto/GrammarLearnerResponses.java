package fit.iuh.engolearn.grammar.dto;

import fit.iuh.engolearn.grammar.model.enums.GrammarBlockType;
import fit.iuh.engolearn.grammar.model.enums.GrammarLearningStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;

import java.time.Instant;
import java.util.List;

public final class GrammarLearnerResponses {
    private GrammarLearnerResponses() {
    }

    public record RoadmapResponse(GrammarLevel currentLevel, List<GrammarLevel> newlyUnlockedLevels,
                                  List<LevelResponse> levels) { }

    public record LevelResponse(GrammarLevel level, boolean unlocked, long completedTopics, long totalTopics,
                                int percent, String lockHint, List<TopicResponse> topics) { }

    public record TopicResponse(String id, String titleVi, String titleEn, String description, GrammarLevel level,
                                String status, int completedLessons, int totalLessons, int percent) { }

    public record TopicDetailResponse(TopicResponse topic, List<LessonSummaryResponse> lessons) { }

    public record LessonSummaryResponse(String id, int number, String titleVi, String titleEn, String objective,
                                        GrammarLearningStatus status, boolean unlocked, Integer bestScore,
                                        boolean hasUpdate) { }

    public record LessonResponse(String topicId, String id, int number, String titleVi, String titleEn,
                                 String objective, GrammarLearningStatus status, int contentVersion,
                                 List<BlockResponse> blocks) { }

    public record BlockResponse(String id, GrammarBlockType type, String title, String text, String formula,
                                List<ExampleResponse> examples, List<String> ruleItems, String incorrect,
                                String correct, String explanation) { }

    public record ExampleResponse(String english, String vietnamese, String analysis) { }

    public record SeenResponse(int contentVersion) { }

    public record PracticeResponse(String id, String topicId, String lessonId, GrammarPracticeStatus status,
                                   int contentVersion, int answeredCount, int totalQuestions, QuestionResponse question,
                                   List<FeedbackResponse> feedback, ResultResponse result, Instant expiresAt) { }

    public record QuestionResponse(String id, int number, GrammarQuestionType type, String prompt,
                                   List<OptionResponse> options) { }

    public record OptionResponse(String id, String text) { }

    public record FeedbackResponse(String questionId, int number, boolean correct, String submittedAnswer,
                                   String correctAnswer, String explanation, GrammarQuestionType type, String prompt) { }

    public record ResultResponse(int score, int totalQuestions, boolean passed, boolean completed,
                                 boolean topicCompleted, String nextLessonId, boolean archived,
                                 List<GrammarLevel> newlyUnlockedLevels) { }

    public record AnswerResponse(FeedbackResponse feedback, PracticeResponse session) { }
}
