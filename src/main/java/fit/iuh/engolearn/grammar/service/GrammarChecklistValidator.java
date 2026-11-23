package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Block;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Check;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Checklist;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Example;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.LessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Question;
import fit.iuh.engolearn.grammar.mapper.GrammarAdminMapper;
import fit.iuh.engolearn.grammar.model.GrammarLesson;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.utils.GrammarAnswerNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Component
public class GrammarChecklistValidator {
    private static final Pattern BLANK = Pattern.compile("(?<!_)____(?!_)");

    public Checklist lesson(LessonInput input) {
        List<Block> blocks = values(input.blocks());
        List<Question> questions = values(input.questions());
        List<Question> multiple = questions.stream().filter(q -> "MULTIPLE_CHOICE".equals(q.type())).toList();
        List<Question> fill = questions.stream().filter(q -> "FILL_IN_BLANK".equals(q.type())).toList();
        return new Checklist(List.of(
                check("Tên bài học song ngữ", length(input.titleVi(), 1, 150) && length(input.titleEn(), 1, 150),
                        "Nhập cả hai tên, tối đa 150 ký tự mỗi tên."),
                check("Mục tiêu học tập", length(input.objective(), 1, 300), "Nhập mục tiêu tối đa 300 ký tự."),
                check("Thứ tự bài học hợp lệ", input.order() != null && input.order() > 0, "Chọn vị trí từ 1 trở lên."),
                check("Có từ 1 đến 7 khối nội dung", !blocks.isEmpty() && blocks.size() <= 7, blocks.size() + "/7 khối."),
                check("Các khối đủ trường theo loại", blocks.stream().allMatch(this::blockFields),
                        "Hoàn thiện các trường bắt buộc ở từng khối."),
                check("Mỗi khối Ví dụ hoặc Quy tắc có 1 đến 5 mục", blocks.stream().allMatch(this::blockItemCount),
                        "Thêm hoặc bớt các mục trong khối Ví dụ hoặc Quy tắc."),
                check("Thứ tự khối liên tục", uniqueIds(blocks.stream().map(Block::id).toList()),
                        "Kiểm tra mã khối trùng hoặc thiếu."),
                check("Có đúng 5 câu luyện tập", questions.size() == 5, questions.size() + "/5 câu."),
                check("Có nội dung cho cả 5 câu", questions.size() == 5 && questions.stream().allMatch(q -> present(q.prompt())),
                        "Nhập nội dung cho các câu còn thiếu."),
                check("Có giải thích cho cả 5 câu", questions.size() == 5 && questions.stream().allMatch(q -> present(q.explanation())),
                        "Bổ sung giải thích đáp án cho từng câu."),
                check("Câu trắc nghiệm có đủ 4 lựa chọn", multiple.stream().allMatch(this::choiceOptions),
                        "Nhập đủ phương án A, B, C và D."),
                check("Mỗi câu trắc nghiệm có một đáp án đúng", multiple.stream().allMatch(this::choiceAnswer),
                        "Chọn một phương án đúng trong bốn lựa chọn."),
                check("Câu điền chỗ trống có một ô trống và đáp án", fill.stream().allMatch(this::blankAnswer),
                        "Dùng đúng một cụm ____ và nhập đáp án chính."),
                check("Thứ tự câu và dữ liệu hợp lệ", multiple.size() + fill.size() == questions.size()
                                && uniqueIds(questions.stream().map(Question::id).toList()),
                        "Kiểm tra mã câu trùng, dữ liệu thiếu hoặc loại câu hỏi không được hỗ trợ.")
        ));
    }

    public Checklist topic(GrammarTopic topic) {
        List<GrammarLesson> active = topic.getLessons().stream()
                .filter(item -> item.getStatus() != GrammarStatus.ARCHIVED).toList();
        List<LessonInput> contents = active.stream().map(GrammarAdminMapper::lessonInput).toList();
        boolean any = !active.isEmpty() && active.size() <= 10;
        boolean orderValid = active.stream().allMatch(lesson -> lesson.getOrder() != null)
                && active.stream().map(GrammarLesson::getOrder).sorted().toList()
                .equals(IntStream.rangeClosed(1, active.size()).boxed().toList());
        return new Checklist(List.of(
                check("Thông tin chủ đề hợp lệ", topic.getLevel() != null && length(topic.getTitleVi(), 1, 120)
                                && length(topic.getTitleEn(), 1, 120) && length(topic.getDescription(), 80, 250),
                        "Bổ sung tên song ngữ, trình độ và mô tả từ 80 đến 250 ký tự."),
                check("Có ít nhất một bài học", any, "Chủ đề cần có từ một đến mười bài học."),
                check("Mọi bài đều Sẵn sàng", any && active.stream().allMatch(GrammarLesson::isReady)
                                && contents.stream().allMatch(input -> lesson(input).checks().stream().allMatch(Check::passed)),
                        "Kiểm tra và đánh dấu Sẵn sàng cho các bài còn thiếu."),
                check("Nội dung lý thuyết hợp lệ", any && contents.stream().allMatch(this::validTheory),
                        "Hoàn thiện từ một đến bảy khối nội dung ở mỗi bài."),
                check("Mỗi bài có đủ 5 câu luyện tập", any && contents.stream().allMatch(input -> values(input.questions()).size() == 5),
                        "Bổ sung đủ năm câu luyện tập ở từng bài."),
                check("Câu hỏi đủ đáp án và giải thích", any && contents.stream().allMatch(this::validPractice),
                        "Kiểm tra nội dung, đáp án và giải thích của tất cả các câu."),
                check("Thứ tự bài học liên tục", any && orderValid, "Kiểm tra lại thứ tự các bài học.")
        ));
    }

    private boolean validTheory(LessonInput input) {
        List<Block> blocks = values(input.blocks());
        return blocks.size() >= 1 && blocks.size() <= 7
                && blocks.stream().allMatch(block -> blockFields(block) && blockItemCount(block))
                && uniqueIds(blocks.stream().map(Block::id).toList());
    }

    private boolean validPractice(LessonInput input) {
        List<Question> questions = values(input.questions());
        return questions.size() == 5 && uniqueIds(questions.stream().map(Question::id).toList())
                && questions.stream().allMatch(question -> present(question.prompt()) && present(question.explanation())
                        && switch (question.type()) {
                            case "MULTIPLE_CHOICE" -> choiceOptions(question) && choiceAnswer(question);
                            case "FILL_IN_BLANK" -> blankAnswer(question);
                            case null, default -> false;
                        });
    }

    private boolean blockFields(Block block) {
        return switch (block.type()) {
            case "EXPLANATION" -> present(block.text());
            case "FORMULA" -> present(block.formula());
            case "EXAMPLE" -> values(block.examples()).stream().allMatch(this::translated);
            case "RULE_QUICK_NOTE" -> present(block.title()) && present(block.text())
                    && values(block.ruleItems()).stream().allMatch(this::present);
            case "COMMON_MISTAKE" -> present(block.incorrect()) && present(block.correct()) && present(block.explanation());
            case null, default -> false;
        };
    }

    private boolean blockItemCount(Block block) {
        return switch (block.type()) {
            case "RULE_QUICK_NOTE" -> itemCount(block.ruleItems());
            case "EXAMPLE" -> itemCount(block.examples());
            case null, default -> true;
        };
    }

    private boolean itemCount(List<?> list) {
        return list != null && !list.isEmpty() && list.size() <= 5;
    }

    private boolean translated(Example example) {
        return present(example.english()) && present(example.vietnamese());
    }

    private boolean choiceOptions(Question question) {
        List<String> options = values(question.options());
        return options.size() == 4 && options.stream().allMatch(this::present);
    }

    private boolean choiceAnswer(Question question) {
        return question.correctIndex() != null && question.correctIndex() >= 0
                && question.correctIndex() < 4 && question.correctIndex() < values(question.options()).size()
                && present(question.options().get(question.correctIndex()));
    }

    private boolean blankAnswer(Question question) {
        return question.prompt() != null && BLANK.matcher(question.prompt()).results().count() == 1
                && question.prompt().indexOf("_____") < 0
                && !GrammarAnswerNormalizer.normalize(question.correctAnswer()).isEmpty();
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private boolean length(String value, int min, int max) {
        return present(value) && value.strip().length() >= min && value.strip().length() <= max;
    }

    private boolean uniqueIds(List<String> ids) {
        return ids.stream().allMatch(this::present) && ids.stream().distinct().count() == ids.size();
    }

    private <T> List<T> values(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private Check check(String label, boolean passed, String detail) {
        return new Check(label, passed, passed ? "" : detail);
    }
}
