package fit.iuh.engolearn.assessment.repository;

import fit.iuh.engolearn.assessment.model.*;
import java.util.List;

public interface AssessmentDraftRepository {
    AssessmentDraft bySeries(String series);
    List<AssessmentDraft> summaries(int page);
    AssessmentPaper latestPaper(String series);
    AssessmentPaper sourcePaper(String id);
    void insert(AssessmentDraft draft);
    boolean replace(String expectedRevision, AssessmentDraft draft);
}
