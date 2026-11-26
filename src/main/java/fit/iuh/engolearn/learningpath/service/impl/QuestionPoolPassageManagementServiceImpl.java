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
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolPassagesRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageListItemResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageListResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageAudioResponse;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.exception.PassageUpdateConflictException;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import fit.iuh.engolearn.learningpath.dto.response.DeleteQuestionPoolPassageResponse;
import fit.iuh.engolearn.learningpath.exception.PassageDeleteConflictException;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageCountersResponse;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.PublishQuestionPoolPassageResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessResponse;
import fit.iuh.engolearn.learningpath.exception.PassagePublicationRejectedException;
import fit.iuh.engolearn.learningpath.exception.PassagePublishConflictException;
import fit.iuh.engolearn.learningpath.model.enums.PassageReviewCheck;
import fit.iuh.engolearn.learningpath.model.vo.PassagePublicationReviewVo;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageReadinessService;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final QuestionPoolRepository questions;
    private final CurrentUserProvider users;
    private final QuestionPoolPassageReadinessService readiness;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public PublishQuestionPoolPassageResponse publish(String passageId, PublishQuestionPoolPassageRequest request) {
        String actor = users.requireCurrentUserId();
        var source = passages.findReadinessSource(passageId).orElseThrow(QuestionPoolPassageNotFoundException::new);
        if (!"DRAFT".equals(source.status()) || !request.revision().equals(source.revision())) {
            throw new PassagePublishConflictException("Only the current revision of a DRAFT passage can be published");
        }
        if (request.revision() == Integer.MAX_VALUE) {
            throw new PassagePublishConflictException("Passage revision limit reached");
        }
        QuestionPoolPassageReadinessResponse evaluation;
        try {
            evaluation = readiness.check(passageId);
        } catch (QuestionPoolPassageNotFoundException exception) {
            throw new PassagePublishConflictException("Passage was removed during readiness evaluation");
        }
        if (!"DRAFT".equals(evaluation.status()) || !request.revision().equals(evaluation.revision())) {
            throw new PassagePublishConflictException("Passage changed during readiness evaluation; reload before publishing");
        }
        if (!evaluation.structurallyValid()) {
            throw new PassagePublicationRejectedException("PASSAGE_NOT_READY", "Passage has publication blockers", evaluation);
        }
        var required = evaluation.requiredReviewChecks().stream().map(PassageReviewCheck::valueOf).toList();
        if (!request.confirmedReviewChecks().equals(Set.copyOf(required))) {
            throw new PassagePublicationRejectedException("PASSAGE_REVIEW_REQUIRED",
                    "Confirm exactly the required review checks for this passage revision", evaluation);
        }
        var review = PassagePublicationReviewVo.builder().sourceRevision(request.revision())
                .confirmedChecks(required).reviewedBy(actor).reviewedAt(Instant.now()).build();
        var saved = passages.publishDraft(passageId, request.revision(), review)
                .orElseThrow(() -> new PassagePublishConflictException("Passage changed while publishing; reload before retrying"));
        var recorded = saved.getPublicationReview();
        return new PublishQuestionPoolPassageResponse(saved.getPassageId(), saved.getStatus(), saved.getRevision(),
                recorded.getSourceRevision(), recorded.getReviewedBy(), recorded.getReviewedAt(),
                recorded.getConfirmedChecks(), evaluation.warnings());
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public DeleteQuestionPoolPassageResponse delete(String passageId, int revision) {
        users.requireCurrentUserId();
        QuestionPoolPassage passage = passages.findByPassageId(passageId)
                .orElseThrow(QuestionPoolPassageNotFoundException::new);
        if (passage.getStatus() != QuestionPoolContentStatus.DRAFT || passage.getRevision() != revision) {
            throw new PassageDeleteConflictException("Only the current revision of a DRAFT passage can be deleted");
        }
        if (questions.hasPassageReferences(passageId)) {
            throw new PassageDeleteConflictException("Passage is still referenced by questions");
        }
        if (!passages.deleteDraft(passageId, revision)) {
            throw new PassageDeleteConflictException("Passage changed while deleting; reload before retrying");
        }
        return new DeleteQuestionPoolPassageResponse(passageId, true);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageResponse update(String passageId, UpdateQuestionPoolPassageRequest request,
            InputStream audio, String filename, String contentType, long sizeBytes) {
        String actor = users.requireCurrentUserId();
        QuestionPoolPassage passage = passages.findByPassageId(passageId)
                .orElseThrow(QuestionPoolPassageNotFoundException::new);
        if (passage.getStatus() != QuestionPoolContentStatus.DRAFT || passage.getRevision() != request.revision()) {
            throw new PassageUpdateConflictException("Only the current revision of a DRAFT passage can be edited");
        }
        if (request.skill() == null && request.level() == null && request.title() == null
                && request.text() == null && request.transcript() == null && audio == null) {
            throw new InvalidPassageException("Supply at least one passage field or an audio file");
        }
        var skill = request.skill() == null ? passage.getSkill() : request.skill();
        var level = request.level() == null ? passage.getLevel() : request.level();
        boolean skillChanged = skill != passage.getSkill();
        if ((skillChanged || level != passage.getLevel())
                && questions.hasIncompatiblePassageReferences(passageId, skill.name(), level.name())) {
            throw new PassageUpdateConflictException("Passage skill or level would mismatch referencing questions");
        }
        String title = request.title() == null ? passage.getTitle() : request.title();
        String text = request.text() == null ? passage.getText() : request.text();
        String transcript = request.transcript() == null ? passage.getTranscript() : request.transcript();
        if (blank(title)) throw new InvalidPassageException("Passage title must not be blank");
        if (skill == QuestionPoolSkill.LISTENING) {
            if (blank(transcript) || (request.text() != null && !blank(request.text()))) {
                throw new InvalidPassageException("Listening requires transcript and must not contain Reading text");
            }
            if (audio == null && blank(passage.getAudioMediaId())
                    && (passage.getAudio() == null || blank(passage.getAudio().getUrl()))) {
                throw new InvalidPassageException("Listening requires an existing audio reference or a new audio file");
            }
        } else if (blank(text) || audio != null || (request.transcript() != null && !blank(request.transcript()))) {
            throw new InvalidPassageException("Reading requires text and must not contain audio or transcript");
        }
        boolean replaceAudio = audio != null || (skillChanged && skill == QuestionPoolSkill.READING);
        if (audio != null) passage.setAudioMediaId(uploadAudio(audio, filename, contentType, sizeBytes));
        else if (replaceAudio) passage.setAudioMediaId(null);
        passage.setSkill(skill);
        passage.setLevel(level);
        passage.setTitle(title.strip());
        passage.setText(skill == QuestionPoolSkill.READING ? text.strip() : null);
        passage.setTranscript(skill == QuestionPoolSkill.LISTENING ? transcript.strip() : null);
        passage.setUpdatedAt(Instant.now());
        passage.setUpdatedBy(actor);
        var saved = passages.updateDraft(passage, request.revision(), replaceAudio)
                .orElseThrow(() -> new PassageUpdateConflictException("Passage changed while saving; reload before editing"));
        return response(saved);
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageDetailResponse detail(String passageId) {
        QuestionPoolPassage passage = passages.findByPassageId(passageId)
                .orElseThrow(QuestionPoolPassageNotFoundException::new);
        QuestionPoolPassageAudioResponse audio = null;
        if (!blank(passage.getAudioMediaId())) {
            var delivery = media.resolve(passage.getAudioMediaId());
            audio = new QuestionPoolPassageAudioResponse(delivery.url().toString(), delivery.expiresAt(),
                    delivery.contentType(), null, null);
        } else if (passage.getAudio() != null && !blank(passage.getAudio().getUrl())) {
            var legacy = passage.getAudio();
            audio = new QuestionPoolPassageAudioResponse(legacy.getUrl(), null, null,
                    legacy.getDurationSec(), legacy.getFormat());
        }
        return new QuestionPoolPassageDetailResponse(passage.getPassageId(), passage.getSkill(),
                passage.getLevel(), passage.getTitle(), passage.getText(), passage.getTranscript(),
                passage.getAudioMediaId(), audio, passage.getStatus(), passage.getRevision(),
                passage.getCreatedBy(), passage.getCreatedAt(), passage.getUpdatedAt());
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolPassageListResponse list(ListQuestionPoolPassagesRequest request) {
        var page = passages.search(request.keyword(), request.skill(), request.level(), request.status(),
                request.page(), request.size());
        var passageIds = page.getContent().stream().map(QuestionPoolPassage::getPassageId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        var referenceCounts = passageIds.isEmpty() ? java.util.Map.<String, Long>of() : questions.countReferencesByPassageIds(passageIds);
        var items = page.getContent().stream().map(passage -> new QuestionPoolPassageListItemResponse(
                passage.getPassageId(), passage.getTitle(), passage.getSkill(), passage.getLevel(),
                passage.getStatus(), passage.getRevision(), !blank(passage.getAudioMediaId())
                        || (passage.getAudio() != null && !blank(passage.getAudio().getUrl())),
                passage.getPassageId() == null ? 0L : referenceCounts.getOrDefault(passage.getPassageId(), 0L),
                passage.getCreatedAt(), passage.getUpdatedAt())).toList();
        var counts = passages.countByStatus();
        var counters = new QuestionPoolPassageCountersResponse(counts.total(), counts.draft(), counts.active(), counts.retired());
        return new QuestionPoolPassageListResponse(items, request.page(), request.size(),
                page.getTotalElements(), page.getTotalPages(), counters);
    }

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
            mediaId = uploadAudio(audio, filename, contentType, sizeBytes);
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
        return response(saved);
    }

    private QuestionPoolPassageResponse response(QuestionPoolPassage saved) {
        return new QuestionPoolPassageResponse(saved.getPassageId(), saved.getSkill(), saved.getLevel(),
                saved.getTitle(), saved.getText(), saved.getTranscript(), saved.getAudioMediaId(),
                saved.getStatus(), saved.getRevision(), saved.getCreatedBy(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    private String uploadAudio(InputStream audio, String filename, String contentType, long sizeBytes) {
        if (audio == null || sizeBytes <= 0 || sizeBytes > MAX_AUDIO_BYTES) {
            throw new InvalidPassageException("Listening requires a non-empty audio file no larger than 25 MiB");
        }
        if (contentType == null || !AUDIO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidPassageException("Audio must be MP3 or WAV");
        }
        return media.upload(new UploadMediaCommandDto("LEARNING_PATH_PASSAGE_AUDIO",
                MediaAccessPolicy.PROTECTED, filename, contentType, sizeBytes, audio)).mediaId();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
