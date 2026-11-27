package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionReadinessIssueResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionReadinessResponse;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolQuestionNotFoundException;
import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionReadinessSource;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageReadinessService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionReadinessService;
import fit.iuh.engolearn.learningpath.utils.QuestionPoolMicroSkills;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionPoolQuestionReadinessServiceImpl implements QuestionPoolQuestionReadinessService {
    private final QuestionPoolQuestionRepository questions;
    private final QuestionPoolPassageRepository passages;
    private final QuestionPoolPassageReadinessService passageReadiness;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionReadinessResponse check(String questionId) {
        var source = questions.findReadinessSource(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
        var blockers = new ArrayList<QuestionPoolQuestionReadinessIssueResponse>();
        var warnings = new ArrayList<QuestionPoolQuestionReadinessIssueResponse>();
        for (String field : source.malformedFields()) blockers.add(issue("INVALID_STORED_FIELD", field, "Trường dữ liệu đã lưu không đúng kiểu."));
        if (!"DRAFT".equals(source.status())) blockers.add(issue("QUESTION_NOT_DRAFT", "status", "Chỉ câu hỏi bản nháp được xuất bản."));
        if (source.revision() == null || source.revision() < 1) blockers.add(issue("INVALID_REVISION", "revision", "Câu hỏi cần revision hợp lệ."));
        else if (source.revision() == Integer.MAX_VALUE) blockers.add(issue("REVISION_EXHAUSTED", "revision", "Revision đã đạt giới hạn."));
        if (!"READING".equals(source.skill()) && !"LISTENING".equals(source.skill())) blockers.add(issue("INVALID_SKILL", "skill", "Kỹ năng phải là READING hoặc LISTENING."));
        if (!QuestionPoolMicroSkills.belongsTo(source.microSkill(), source.skill())) blockers.add(issue("INVALID_MICRO_SKILL", "microSkill", "Micro-skill canonical phải thuộc đúng kỹ năng lớn."));
        if (Arrays.stream(CefrLevel.values()).noneMatch(level -> level.name().equals(source.level()))) blockers.add(issue("INVALID_LEVEL", "level", "Trình độ phải thuộc CEFR A1–C2."));
        validateText(source.prompt(), 50000, "prompt", "PROMPT_REQUIRED", blockers);
        validateText(source.explanation(), 50000, "explanation", "EXPLANATION_REQUIRED", blockers);
        checkOptions(source, blockers);
        Integer passageRevision = checkPassage(source, blockers, warnings);
        var reviews = new ArrayList<>(List.of(QuestionReviewCheck.MICRO_SKILL_APPROPRIATE, QuestionReviewCheck.LEVEL_APPROPRIATE,
                QuestionReviewCheck.SINGLE_BEST_ANSWER_WITH_EVIDENCE, QuestionReviewCheck.DISTRACTORS_PLAUSIBLE,
                QuestionReviewCheck.EXPLANATION_SUPPORTED, QuestionReviewCheck.STIMULUS_SUFFICIENT, QuestionReviewCheck.NO_ANSWER_LEAKAGE));
        if ("LISTENING".equals(source.skill())) reviews.addAll(List.of(QuestionReviewCheck.AUDIO_MATCHES_TRANSCRIPT, QuestionReviewCheck.AUDIO_QUALITY_REVIEWED));
        return new QuestionPoolQuestionReadinessResponse(questionId, source.revision(), source.status(), blockers.isEmpty(),
                source.passageId(), passageRevision, blockers, warnings, reviews);
    }

    private void checkOptions(QuestionPoolQuestionReadinessSource source, List<QuestionPoolQuestionReadinessIssueResponse> blockers) {
        Set<String> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        boolean invalidIds = false, duplicateTexts = false;
        if (source.options() == null || source.options().size() != 4) blockers.add(issue("OPTIONS_COUNT_INVALID", "options", "Cần đúng bốn lựa chọn A–D."));
        if (source.options() != null) {
            for (int i = 0; i < source.options().size(); i++) {
                var option = source.options().get(i);
                if (option.optionId() == null || !Set.of("A", "B", "C", "D").contains(option.optionId()) || !ids.add(option.optionId())) invalidIds = true;
                validateText(option.text(), 10000, "options[" + i + "].text", "OPTION_TEXT_REQUIRED", blockers);
                if (!blank(option.text()) && !texts.add(option.text().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))) duplicateTexts = true;
            }
        }
        if (invalidIds || !ids.equals(Set.of("A", "B", "C", "D"))) blockers.add(issue("OPTION_IDS_INVALID", "options", "Mỗi mã A–D phải xuất hiện đúng một lần."));
        if (duplicateTexts) blockers.add(issue("OPTION_TEXTS_DUPLICATE", "options", "Bốn lựa chọn phải khác nội dung."));
        if (source.correctOptionId() == null || !Set.of("A", "B", "C", "D").contains(source.correctOptionId()) || !ids.contains(source.correctOptionId())) {
            blockers.add(issue("CORRECT_OPTION_INVALID", "correctOptionId", "Cần một đáp án đúng thuộc các lựa chọn A–D."));
        }
    }

    private Integer checkPassage(QuestionPoolQuestionReadinessSource source, List<QuestionPoolQuestionReadinessIssueResponse> blockers,
            List<QuestionPoolQuestionReadinessIssueResponse> warnings) {
        if (blank(source.passageId())) {
            if ("LISTENING".equals(source.skill())) blockers.add(issue("PASSAGE_REQUIRED", "passageId", "Listening bắt buộc có passage."));
            return null;
        }
        if (source.passageId().length() > 100) blockers.add(issue("PASSAGE_ID_INVALID", "passageId", "Mã passage vượt 100 ký tự."));
        var passage = passages.findReadinessSource(source.passageId()).orElse(null);
        if (passage == null) {
            blockers.add(issue("PASSAGE_NOT_FOUND", "passageId", "Không tìm thấy passage được tham chiếu."));
            return null;
        }
        if (!Objects.equals(source.skill(), passage.skill()) || !Objects.equals(source.level(), passage.level())) {
            blockers.add(issue("PASSAGE_CLASSIFICATION_MISMATCH", "passageId", "Passage phải cùng kỹ năng và trình độ với câu hỏi."));
        }
        if (passage.revision() != null && passage.revision() == Integer.MAX_VALUE) blockers.add(issue("PASSAGE_REVISION_EXHAUSTED", "passage.revision", "Revision passage đã đạt giới hạn."));
        try {
            var evaluation = passageReadiness.checkForQuestionPublication(source.passageId());
            if (!Objects.equals(passage.revision(), evaluation.revision()) || !Objects.equals(passage.status(), evaluation.status())) {
                blockers.add(issue("PASSAGE_CHANGED", "passageId", "Passage đã thay đổi trong lúc kiểm tra; cần kiểm tra lại."));
            }
            evaluation.blockers().forEach(value -> blockers.add(issue(value.code(), "passage." + value.field(), value.message())));
            evaluation.warnings().forEach(value -> warnings.add(issue(value.code(), "passage." + value.field(), value.message())));
        } catch (QuestionPoolPassageNotFoundException missing) {
            blockers.add(issue("PASSAGE_NOT_FOUND", "passageId", "Không tìm thấy passage được tham chiếu."));
        }
        return passage.revision();
    }

    private void validateText(String value, int maximum, String field, String missingCode, List<QuestionPoolQuestionReadinessIssueResponse> blockers) {
        if (blank(value)) blockers.add(issue(missingCode, field, "Nội dung bắt buộc chưa được nhập."));
        else if (value.length() > maximum) blockers.add(issue("CONTENT_TOO_LONG", field, "Nội dung vượt giới hạn " + maximum + " ký tự."));
    }

    private QuestionPoolQuestionReadinessIssueResponse issue(String code, String field, String message) {
        return new QuestionPoolQuestionReadinessIssueResponse(code, field, message);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
