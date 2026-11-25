package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageResponse;
import fit.iuh.engolearn.learningpath.exception.InvalidPassageException;
import fit.iuh.engolearn.learningpath.model.QuestionPoolPassage;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionPoolPassageManagementServiceImpl implements QuestionPoolPassageManagementService {
    private static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave");
    private final QuestionPoolPassageRepository passages;
    private final MediaManagement media;
    private final CurrentUserProvider users;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageResponse create(CreateQuestionPoolPassageRequest request,
            InputStream audio, String filename, String contentType, long sizeBytes) {
        String actor = users.requireCurrentUserId();
        String mediaId = null;
        if (request.skill() == QuestionPoolSkill.LISTENING) {
            if (blank(request.transcript()) || !blank(request.text())) {
                throw new InvalidPassageException("Listening requires transcript and must not contain Reading text");
            }
            if (audio == null || sizeBytes <= 0 || sizeBytes > MAX_AUDIO_BYTES) {
                throw new InvalidPassageException("Listening requires a non-empty audio file no larger than 25 MiB");
            }
            if (contentType == null || !AUDIO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                throw new InvalidPassageException("Audio must be MP3 or WAV");
            }
            mediaId = media.upload(new UploadMediaCommandDto("LEARNING_PATH_PASSAGE_AUDIO",
                    MediaAccessPolicy.PROTECTED, filename, contentType, sizeBytes, audio)).mediaId();
        } else {
            if (blank(request.text()) || !blank(request.transcript()) || audio != null) {
                throw new InvalidPassageException("Reading requires text and must not contain audio or transcript");
            }
        }
        Instant now = Instant.now();
        QuestionPoolPassage saved = passages.insert(QuestionPoolPassage.builder()
                .passageId("LP-PASS-" + UUID.randomUUID())
                .skill(request.skill()).level(request.level()).title(request.title().strip())
                .text(request.skill() == QuestionPoolSkill.READING ? request.text().strip() : null)
                .transcript(request.skill() == QuestionPoolSkill.LISTENING ? request.transcript().strip() : null)
                .audioMediaId(mediaId).status(QuestionPoolContentStatus.DRAFT).revision(1)
                .createdBy(actor).createdAt(now).updatedAt(now).build());
        return new QuestionPoolPassageResponse(saved.getPassageId(), saved.getSkill(), saved.getLevel(),
                saved.getTitle(), saved.getText(), saved.getTranscript(), saved.getAudioMediaId(),
                saved.getStatus(), saved.getRevision(), saved.getCreatedBy(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
