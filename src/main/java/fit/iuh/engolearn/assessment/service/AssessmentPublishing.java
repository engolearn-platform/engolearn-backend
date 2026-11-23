package fit.iuh.engolearn.assessment.service;

import fit.iuh.engolearn.assessment.dto.response.AssessmentDraftResponse;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.dto.response.ManagedAssessmentPaperResponse;
import java.util.List;

public interface AssessmentPublishing {
    AssessmentDraftResponse preview(String series, String revision);
    AssessmentMediaResponse previewAudio(String series, String revision, int index);
    void review(String series, String revision);
    ManagedAssessmentPaperResponse publish(String series, String revision);
    void deleteDraft(String series, String revision);
    List<ManagedAssessmentPaperResponse> papers(int page);
    ManagedAssessmentPaperResponse archive(String series, int version);
}
