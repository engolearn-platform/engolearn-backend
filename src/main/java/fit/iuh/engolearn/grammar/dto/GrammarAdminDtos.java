package fit.iuh.engolearn.grammar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class GrammarAdminDtos {
    private GrammarAdminDtos() {
    }

    public record TopicInput(@PositiveOrZero Long version, @Positive Integer order,
                             @NotBlank @Size(max = 120) String titleVi,
                             @NotBlank @Size(max = 120) String titleEn,
                             @NotBlank @Size(max = 250) String description,
                             @NotNull @Pattern(regexp = "A1|A2|B1|B2") String level) {
    }

    public record CreateLessonInput(@NotNull @PositiveOrZero Long version, @Positive Integer order,
                                    @NotBlank @Size(max = 150) String titleVi,
                                    @NotBlank @Size(max = 150) String titleEn,
                                    @NotBlank @Size(max = 300) String objective,
                                    List<@NotNull String> tags) {
    }

    public record LessonInput(@NotNull @PositiveOrZero Long version, @Positive Integer order,
                              @NotBlank @Size(max = 150) String titleVi,
                              @NotBlank @Size(max = 150) String titleEn,
                              @NotBlank @Size(max = 300) String objective,
                              List<@NotNull String> tags,
                              @Size(max = 7) List<@NotNull @Valid Block> blocks,
                              @Size(max = 5) List<@NotNull @Valid Question> questions) {
    }

    public record Block(String id,
                        @NotNull @Pattern(regexp = "EXPLANATION|FORMULA|EXAMPLE|RULE_QUICK_NOTE|COMMON_MISTAKE") String type,
                        String title, String text, String formula,
                        @Size(max = 5) List<@NotNull Example> examples,
                        @Size(max = 5) List<@NotNull String> ruleItems,
                        String incorrect, String correct, String explanation) {
    }

    public record Example(String english, String vietnamese, String analysis) {
    }

    public record Question(String id, @NotNull @Pattern(regexp = "MULTIPLE_CHOICE|FILL_IN_BLANK") String type,
                           String prompt, String explanation, @Size(max = 4) List<String> options,
                           Integer correctIndex, String correctAnswer, List<@NotNull String> acceptedAnswers) {
    }

    public record VersionInput(@NotNull @PositiveOrZero Long version, Boolean confirmPublish) {
    }

    public record Topic(String id, String code, String titleVi, String titleEn, String description, String level,
                        Integer order, String status, long version, Instant updatedAt, Instant firstPublishedAt,
                        long readyCount, long lessonCount, List<Lesson> lessons) {
    }

    public record Lesson(String id, String code, Integer order, String status, long version, boolean ready,
                         int contentVersion, String titleVi, String titleEn, String objective, List<String> tags,
                         List<Block> blocks, List<Question> questions, boolean hasDraft, Instant firstPublishedAt,
                         String archiveReason) {
    }

    public record TopicPage(List<Topic> items, long total, Stats stats) {
    }

    public record Stats(long total, long published, long inProgress, long draft, long archived) {
    }

    public record Checklist(List<Check> checks) {
    }

    public record Check(String label, boolean passed, String detail) {
    }
}
