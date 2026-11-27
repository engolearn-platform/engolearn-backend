package fit.iuh.engolearn.assessment.service;

import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import java.util.List;

public interface AssessmentDraftService {
    List<AssessmentSkillOptionResponse> skillCatalog();
    AssessmentDraftResponse get(String series);
    List<DraftSummaryResponse> list(int page);
    AssessmentDraftResponse create(CreateDraftRequest input);
    AssessmentDraftResponse save(String series, DraftContentRequest input);
}
