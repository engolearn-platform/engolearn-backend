package fit.iuh.engolearn.assessment.service;
import fit.iuh.engolearn.assessment.model.*;
import java.time.Instant;
import java.util.*;
public interface AssessmentPaperSelection {
    void validateForPublication(AssessmentPaper p);
    void validateForStart(AssessmentPaper p);
    void validateStructure(AssessmentPaper p);
    AssessmentPaper choose(List<AssessmentPaper> papers,List<AssessmentAttempt> history,Instant now);
}
