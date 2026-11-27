package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessIssueResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessResponse;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.learningpath.model.enums.PassageReviewCheck;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageReadinessSource;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageReadinessService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionPoolPassageReadinessServiceImpl implements QuestionPoolPassageReadinessService {
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave");
    private final QuestionPoolPassageRepository passages;
    private final QuestionPoolRepository questions;
    private final MediaManagement media;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageReadinessResponse check(String passageId) {
        return evaluate(passageId, "DRAFT", true);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageReadinessResponse checkForQuestionPublication(String passageId) {
        return evaluate(passageId, "ACTIVE", false);
    }

    private QuestionPoolPassageReadinessResponse evaluate(String passageId, String requiredStatus, boolean validateReferences) {
        var source = passages.findReadinessSource(passageId).orElseThrow(QuestionPoolPassageNotFoundException::new);
        List<QuestionPoolPassageReadinessIssueResponse> blockers = new ArrayList<>();
        List<QuestionPoolPassageReadinessIssueResponse> warnings = new ArrayList<>();
        List<String> reviews = new ArrayList<>(List.of(PassageReviewCheck.LEVEL_APPROPRIATE.name(),
                PassageReviewCheck.CONTENT_SUPPORTS_INTENDED_QUESTIONS.name()));
        for (String field : source.malformedFields()) {
            blockers.add(issue("INVALID_STORED_FIELD", field, "Trường dữ liệu đã lưu không đúng kiểu."));
        }
        if (!requiredStatus.equals(source.status())) {
            blockers.add("DRAFT".equals(requiredStatus)
                    ? issue("PASSAGE_NOT_DRAFT", "status", "Chỉ passage bản nháp được xuất bản.")
                    : issue("PASSAGE_NOT_ACTIVE", "status", "Passage phải đang sử dụng trước khi xuất bản câu hỏi."));
        }
        if (source.revision() == null || source.revision() < 1) {
            blockers.add(issue("INVALID_REVISION", "revision", "Passage cần revision hợp lệ trước khi xuất bản."));
        }
        validateText(source.title(), 200, "title", "TITLE_REQUIRED", blockers, true);
        if (Arrays.stream(CefrLevel.values()).noneMatch(level -> level.name().equals(source.level()))) {
            blockers.add(issue("INVALID_LEVEL", "level", "Trình độ phải thuộc CEFR A1–C2."));
        }
        if ("READING".equals(source.skill())) {
            validateText(source.text(), 50000, "text", "READING_TEXT_REQUIRED", blockers, true);
            if (!blank(source.transcript()) || !blank(source.audioMediaId()) || source.hasLegacyAudio()) {
                blockers.add(issue("READING_HAS_LISTENING_CONTENT", "skill", "Passage Reading không được chứa audio hoặc transcript."));
            }
        } else if ("LISTENING".equals(source.skill())) {
            validateText(source.transcript(), 50000, "transcript", "TRANSCRIPT_REQUIRED", blockers, true);
            if (!blank(source.text())) blockers.add(issue("LISTENING_HAS_READING_TEXT", "text", "Passage Listening không được chứa nội dung Reading."));
            reviews.addAll(List.of(PassageReviewCheck.AUDIO_MATCHES_TRANSCRIPT.name(), PassageReviewCheck.AUDIO_QUALITY_REVIEWED.name()));
            checkAudio(source, blockers, warnings);
        } else {
            blockers.add(issue("INVALID_SKILL", "skill", "Kỹ năng phải là READING hoặc LISTENING."));
        }
        long total = questions.countPassageReferences(passageId);
        long incompatible = questions.countIncompatiblePassageReferences(passageId, source.skill(), source.level());
        if (validateReferences && incompatible > 0) {
            blockers.add(issue("INCOMPATIBLE_QUESTION_REFERENCES", "questions", "Có câu hỏi tham chiếu sai kỹ năng hoặc trình độ của passage."));
        }
        return new QuestionPoolPassageReadinessResponse(source.passageId(), source.revision(), source.status(),
                blockers.isEmpty(), total, incompatible, blockers, warnings, reviews);
    }

    private void checkAudio(QuestionPoolPassageReadinessSource source,
            List<QuestionPoolPassageReadinessIssueResponse> blockers,
            List<QuestionPoolPassageReadinessIssueResponse> warnings) {
        if (!blank(source.audioMediaId())) {
            try {
                var delivery = media.resolve(source.audioMediaId());
                if (delivery.url() == null || !validAudioUrl(delivery.url().toString(), false)) {
                    blockers.add(issue("AUDIO_DELIVERY_URL_INVALID", "audio", "Media chưa cung cấp liên kết audio hợp lệ."));
                }
                if (!AUDIO_TYPES.contains(delivery.contentType() == null ? "" : delivery.contentType())) {
                    blockers.add(issue("AUDIO_CONTENT_TYPE_INVALID", "audio", "Media phải cung cấp audio MP3 hoặc WAV."));
                }
                if (delivery.expiresAt() != null && !delivery.expiresAt().isAfter(Instant.now())) {
                    blockers.add(issue("AUDIO_DELIVERY_EXPIRED", "audio", "Liên kết audio vừa cấp đã hết hạn, cần kiểm tra lại."));
                }
            } catch (MediaNotFoundException exception) {
                blockers.add(issue("AUDIO_MEDIA_NOT_FOUND", "audio", "Không tìm thấy tài nguyên audio được tham chiếu."));
            }
        } else if (blank(source.legacyAudioUrl())) {
            blockers.add(issue("AUDIO_MISSING", "audio", "Passage Listening chưa có audio."));
        } else if (!validAudioUrl(source.legacyAudioUrl(), true)) {
            blockers.add(issue("AUDIO_URL_INVALID", "audio", "Liên kết audio cũ không hợp lệ."));
        } else {
            warnings.add(issue("LEGACY_AUDIO_PLAYBACK_UNVERIFIED", "audio", "Liên kết audio cũ chưa được xác minh phát được; admin cần nghe thử."));
        }
    }

    private boolean validAudioUrl(String value, boolean allowRelative) {
        if (value.contains("\\")) return false;
        try {
            URI uri = URI.create(value);
            if (allowRelative && value.startsWith("/") && !value.startsWith("//") && !uri.isAbsolute()) return true;
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateText(String value, int maximum, String field, String missingCode,
            List<QuestionPoolPassageReadinessIssueResponse> blockers, boolean required) {
        if (required && blank(value)) blockers.add(issue(missingCode, field, "Nội dung bắt buộc chưa được nhập."));
        else if (value != null && value.length() > maximum) blockers.add(issue("CONTENT_TOO_LONG", field, "Nội dung vượt giới hạn " + maximum + " ký tự."));
    }

    private QuestionPoolPassageReadinessIssueResponse issue(String code, String field, String message) {
        return new QuestionPoolPassageReadinessIssueResponse(code, field, message);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
