package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.contract.MicroSkillCatalog;
import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.dto.response.ManagedAssessmentPaperResponse;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.mapper.AssessmentDraftMapper;
import fit.iuh.engolearn.assessment.mapper.AssessmentMapper;
import fit.iuh.engolearn.assessment.model.AssessmentDraft;
import fit.iuh.engolearn.assessment.model.AssessmentPaper;
import fit.iuh.engolearn.assessment.model.AssessmentTimeline;
import fit.iuh.engolearn.assessment.model.enums.AssessmentUnitKind;
import fit.iuh.engolearn.assessment.repository.AssessmentDraftRepository;
import fit.iuh.engolearn.assessment.service.AssessmentMedia;
import fit.iuh.engolearn.assessment.service.AssessmentPublishing;
import fit.iuh.engolearn.assessment.service.AssessmentPaperSelection;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class AssessmentPublishingService implements AssessmentPublishing {
    private final AssessmentDraftRepository drafts;
    private final AssessmentPaperSelection selection;
    private final AssessmentTransactions transactions;
    private final AssessmentMedia audio;
    private final CurrentUserProvider users;
    private final MicroSkillCatalog microSkills;

    public AssessmentDraftResponse preview(String series, String revision) {
        var draft = requireDraft(series, revision);
        validate(draft);
        if (!drafts.markPreviewed(draft, users.requireCurrentUserId())) throw stale();
        return AssessmentDraftMapper.response(draft);
    }

    public AssessmentMediaResponse previewAudio(String series, String revision, int index) {
        var draft = requireDraft(series, revision);
        if (index < 0 || index >= draft.getUnits().size() || !draft.getUnits().get(index).hasAudio()) {
            throw new AssessmentException(404, "AUDIO_NOT_FOUND", "Phần này không có audio.");
        }
        var unit = draft.getUnits().get(index);
        return audio.delivery(unit, unit.getKind() == AssessmentUnitKind.LISTENING_SINGLE
                ? AssessmentTimeline.listeningSingleAudioLimitSeconds() : 120);
    }

    public void review(String series, String revision) {
        var draft = requireDraft(series, revision);
        String reviewer = users.requireCurrentUserId();
        if (draft.getPreviewedBy() == null || !draft.getPreviewedBy().contains(reviewer)) {
            throw new AssessmentException(409, "DRAFT_PREVIEW_REQUIRED", "Hãy xem thử đúng bản nháp trước khi xác nhận.");
        }
        if (!drafts.markReviewed(draft, reviewer)) throw stale();
    }

    public ManagedAssessmentPaperResponse publish(String series, String revision) {
        var released = drafts.publishedFromRevision(series, revision);
        if (released != null) return response(released);

        AssessmentPaper paper;
        try {
            paper = validate(requireReviewed(series, revision));
            paper.setUnits(AssessmentMapper.snapshot(paper).getUnits());
            for (var unit : paper.getUnits()) unit.setAudioMediaId(audio.durableReference(unit));
        } catch (AssessmentException ex) {
            released = drafts.publishedFromRevision(series, revision);
            if (released != null) return response(released);
            throw ex;
        }

        return transactions.execute("paper:" + series, () -> {
            var replay = drafts.publishedFromRevision(series, revision);
            if (replay != null) return response(replay);
            var draft = requireReviewed(series, revision);
            var latest = drafts.latestPaper(series);
            if (latest != null && latest.getVersion() >= draft.getVersion()) throw stale();
            if (!drafts.delete(draft)) throw stale();
            drafts.deactivatePublished(series);
            drafts.insertPaper(paper);
            return response(paper);
        });
    }

    private AssessmentDraft requireReviewed(String series, String revision) {
        var draft = requireDraft(series, revision);
        if (!revision.equals(draft.getReviewedRevision())
                || draft.getPreviewedBy() == null || draft.getPreviewedBy().isEmpty()) {
            throw new AssessmentException(409, "DRAFT_REVIEW_REQUIRED", "Xem thử và xác nhận đúng bản nháp trước khi xuất bản.");
        }
        return draft;
    }

    public void deleteDraft(String series, String revision) {
        var draft = drafts.bySeries(series);
        if (draft == null) return;
        if (!revision.equals(draft.getRevision()) || !drafts.delete(draft)) throw stale();
    }

    public List<ManagedAssessmentPaperResponse> papers(int page) {
        return drafts.papers(page).stream().map(AssessmentPublishingService::response).toList();
    }

    public ManagedAssessmentPaperResponse archive(String series, int version) {
        return transactions.execute("paper:" + series, () -> {
            var paper = drafts.paperVersion(series, version);
            if (paper == null) throw new AssessmentException(404, "PAPER_NOT_FOUND", "Không tìm thấy phiên bản đề.");
            if (paper.isPublished()) {
                drafts.deactivatePaper(paper.getId());
                paper.setPublished(false);
            }
            return response(paper);
        });
    }

    private AssessmentDraft requireDraft(String series, String revision) {
        var draft = drafts.bySeries(series);
        if (draft == null) throw new AssessmentException(404, "DRAFT_NOT_FOUND", "Không tìm thấy bản nháp.");
        if (!revision.equals(draft.getRevision())) throw stale();
        return draft;
    }

    private AssessmentPaper validate(AssessmentDraft draft) {
        if (draft.getName() == null || draft.getName().isBlank()) {
            throw new AssessmentException(422, "PAPER_INVALID", "Tên bộ đề còn trống.");
        }
        if (draft.getSkills() != null) {
            for (var skill : draft.getSkills()) {
                if (skill != null && microSkills.find(skill.getCode())
                        .filter(entry -> !entry.availableForAssessment()).isPresent()) {
                    throw new AssessmentException(422, "MICRO_SKILL_UNAVAILABLE",
                            "Kỹ năng " + skill.getCode() + " đã ngừng dùng hoặc chưa có mapping.");
                }
            }
        }
        var paper = AssessmentPaper.builder()
                .id("paper:" + draft.getSeries() + ":" + draft.getVersion())
                .series(draft.getSeries()).name(draft.getName()).version(draft.getVersion())
                .rulesVersion(2).published(true).demonstration(draft.isDemonstration())
                .publishedDraftRevision(draft.getRevision()).units(draft.getUnits()).skills(draft.getSkills()).build();
        try {
            selection.validateForPublication(paper);
        } catch (AssessmentException ex) {
            if ("PAPER_INVALID".equals(ex.code())) {
                throw new AssessmentException(422, ex.code(), ex.getMessage(), ex);
            }
            throw ex;
        }
        return paper;
    }

    private static ManagedAssessmentPaperResponse response(AssessmentPaper paper) {
        return new ManagedAssessmentPaperResponse(paper.getId(), paper.getSeries(), paper.getName(),
                paper.getVersion(), paper.isPublished());
    }

    private static AssessmentException stale() {
        return new AssessmentException(409, "STALE_DRAFT", "Bản nháp đã thay đổi. Hãy mở lại bản mới nhất.");
    }
}
