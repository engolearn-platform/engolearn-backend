package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.contract.AssessmentSkillCatalog;
import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.mapper.AssessmentDraftMapper;
import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.repository.AssessmentDraftRepository;
import fit.iuh.engolearn.assessment.service.AssessmentDraftService;
import fit.iuh.engolearn.assessment.service.AssessmentCloudAudio;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import java.util.*;
import java.util.stream.IntStream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class AssessmentDraftServiceImpl implements AssessmentDraftService {
    private final AssessmentDraftRepository drafts;
    private final AssessmentCloudAudio audio;
    private final AssessmentTransactions transactions;

    private AssessmentDraft requireDraft(String series) {
        var draft = drafts.bySeries(series);
        if (draft == null) throw new AssessmentException(404, "DRAFT_NOT_FOUND", "Không tìm thấy bản nháp.");
        return draft;
    }
    public List<AssessmentSkillOptionResponse> skillCatalog() {
        return Arrays.stream(AssessmentSkillCatalog.values())
                .map(skill -> new AssessmentSkillOptionResponse(skill.name(), skill.label(), skill.section())).toList();
    }

    public AssessmentDraftResponse get(String series) { return AssessmentDraftMapper.response(requireDraft(series)); }
    public List<DraftSummaryResponse> list(int page) {
        return drafts.summaries(page).stream()
                .map(d -> new DraftSummaryResponse(d.getSeries(), d.getName(), d.getVersion())).toList();
    }

    public AssessmentDraftResponse create(CreateDraftRequest input) {
        return transactions.execute("paper:" + input.series(), () -> createDraft(input));
    }

    private AssessmentDraftResponse createDraft(CreateDraftRequest input) {
        var paper = drafts.latestPaper(input.series());
        if (input.expectedSourceId() != null && (paper == null || !input.expectedSourceId().equals(paper.getId()))) {
            throw new AssessmentException(409, "DRAFT_SOURCE_CHANGED", "Đề nguồn đã thay đổi. Hãy kiểm tra lại phiên bản.");
        }
        var draft = AssessmentDraft.builder().id("draft:" + input.series()).series(input.series()).name(input.name())
                .version(paper == null ? 1 : paper.getVersion() + 1).revision(UUID.randomUUID().toString())
                .skills(List.of()).units(List.of()).sourceAudioUnits(List.of()).build();
        if (paper != null) {
            // Old imports can omit optional arrays. Reject broken elements before inserting the draft.
            if ((paper.getSkills() != null && paper.getSkills().stream().anyMatch(Objects::isNull))
                    || (paper.getUnits() != null && paper.getUnits().stream().anyMatch(u -> u == null
                    || (u.getQuestions() != null && u.getQuestions().stream().anyMatch(Objects::isNull))))) {
                throw new AssessmentException(422, "INVALID_SOURCE_PAPER", "Đề nguồn có kỹ năng, phần hoặc câu bị lỗi.");
            }
            draft.setSourcePaperId(paper.getId());
            draft.setDemonstration(paper.isDemonstration());
            draft.setSkills(paper.getSkills() == null ? List.of() : paper.getSkills());
            draft.setUnits(paper.getUnits() == null ? List.of() : paper.getUnits());
            draft.setSourceAudioUnits(IntStream.range(0, draft.getUnits().size()).boxed()
                    .map(i -> draft.getUnits().get(i).hasAudio() ? i : null).toList());
        }
        try { drafts.insert(draft); }
        catch (DuplicateKeyException e) {
            throw new AssessmentException(409, "DRAFT_EXISTS", "Mã đề này đã có bản nháp. Hãy mở bản đang có.");
        }
        return AssessmentDraftMapper.response(draft);
    }

    public AssessmentDraftResponse save(String series, DraftContentRequest input) {
        var draft = requireDraft(series);
        if (!draft.getRevision().equals(input.revision())) throw stale();
        AssessmentPaper source = draft.getSourcePaperId() == null ? null : drafts.sourcePaper(draft.getSourcePaperId());
        Map<String, AssessmentAudioAsset> uploaded = new HashMap<>();
        for (var unit : input.units()) {
            if (unit.audioSha256() != null) {
                var asset = audio.find(unit.audioSha256());
                if (unit.sourceAudioUnit() != null || unit.kind() == null
                        || !unit.kind().name().startsWith("LISTENING") || asset == null) {
                    throw new AssessmentException(422, "INVALID_ASSESSMENT_AUDIO", "Tham chiếu audio chưa hợp lệ.");
                }
                uploaded.put(unit.audioSha256(), asset);
            }
            var index = unit.sourceAudioUnit();
            if (index != null && (source == null || source.getUnits() == null || index < 0 || index >= source.getUnits().size()
                    || !source.getUnits().get(index).hasAudio())) {
                throw new AssessmentException(400, "INVALID_SOURCE_AUDIO", "Tham chiếu audio nằm ngoài đề nguồn.");
            }
        }
        AssessmentDraftMapper.applyContent(draft, input, source, uploaded);
        draft.setRevision(UUID.randomUUID().toString());
        if (!drafts.replace(input.revision(), draft)) throw stale();
        return AssessmentDraftMapper.response(draft);
    }

    private AssessmentException stale() {
        return new AssessmentException(409, "STALE_DRAFT", "Bản nháp đã thay đổi. Hãy mở lại trước khi lưu.");
    }
}
