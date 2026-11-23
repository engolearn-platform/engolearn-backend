package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.enums.QuizType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TopicManagementItemDetail(
        String id,
        String topicId,
        String title,
        String description,
        Integer order,
        ContentStatus status,
        Integer durationMinutes,
        String versionId,
        Integer version,
        String topicVersionId,
        String previousItemVersionId,
        ChangeType changeType,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Context context,
        List<Vocabulary> vocabularies,
        List<ExpressionBlock> expressions,
        List<QuizModule> quizzes) {

    public record Context(String prompt, String scenarioDescription, List<DialogueTurn> dialogue,
                          Integer durationSec, String tips) {
    }

    public record DialogueTurn(String speaker, String textEn, String textVi, String audioUrl) {
    }

    public record Vocabulary(String vocabId, String senseId, WordSnapshot snapshot, List<WordSample> samples,
                             List<Collocation> collocations, QuickCheck quickCheck, Integer orderIndex, String tips) {
    }

    public record WordSnapshot(String word, String pos, String sense, String describeVi, String phonetic,
                               String audioUrl, String audioEnUrl, String audioUsUrl) {
    }

    public record WordSample(String textEn, String textVi, String audioUrl, Boolean isManual) {
    }

    public record Collocation(String text, CollocationExample example, String descriptionVi) {
    }

    public record CollocationExample(String en, String vi) {
    }

    public record QuickCheck(QuizSentence sentence, Map<String, Object> quizData,
                             Map<String, Object> answerData, String explanation) {
    }

    public record ExpressionBlock(List<ExpressionPurpose> purposes, String tips) {
    }

    public record ExpressionPurpose(String purposeEn, String purposeVi, Integer orderIndex,
                                    List<ExpressionExample> examples) {
    }

    public record ExpressionExample(ExpressionType type, String textEn, String textVi, String usageNote,
                                    String audioUrl, List<String> linkedVocabIds) {
    }

    public record QuizModule(String quizId, QuizType quizType, String title, String instructions,
                             Integer orderIndex, List<QuizQuestion> questions) {
    }

    public record QuizQuestion(String prompt, String audioUrl, List<QuizOption> options, String correct,
                               String explanationOk, String explanationNg, QuizSentence sentence,
                               Map<String, Object> quizData, Map<String, Object> answerData,
                               String explanation, String tips) {
    }

    public record QuizSentence(String textEn, String meaningVi, String audioUrl, String audioUrlSlow,
                               String source, String externalId) {
    }

    public record QuizOption(String label, String textEn, Boolean isCorrect) {
    }
}
