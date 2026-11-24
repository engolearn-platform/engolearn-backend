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
    boolean markPreviewed(AssessmentDraft draft, String userId);
    boolean markReviewed(AssessmentDraft draft, String userId);
    boolean delete(AssessmentDraft draft);
    List<AssessmentPaper> papers(int page);
    AssessmentPaper paperVersion(String series, int version);
    AssessmentPaper publishedFromRevision(String series, String revision);
    void deactivatePublished(String series);
    void deactivatePaper(String id);
    void insertPaper(AssessmentPaper paper);
}
