package fit.iuh.engolearn.assessment.repository;
import fit.iuh.engolearn.assessment.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
public interface AssessmentRepository {
    record DevelopmentBaseline(boolean valid,String level) {}
    void ensureGate(String user);
    void lock(String user);
    AssessmentAttempt attempt(String id,String user);
    AssessmentAttempt completedAttempt(String id,String user,boolean includeDemonstration);
    Set<String> completedAttemptIds(String user,Set<String> ids,boolean includeDemonstration);
    AssessmentAttempt request(String user,String request);
    AssessmentAttempt active(String user);
    List<AssessmentAttempt> history(String user,int page,int size,boolean includeDemonstration);
    List<AssessmentAttempt> valid(String user);
    List<AssessmentPaper> published();
    List<AssessmentAttempt> due(Instant now);
    AssessmentAttempt save(AssessmentAttempt a);
    AssessmentProfile profile(String user);
    void saveProfile(AssessmentProfile profile);
    boolean revoked(String user,String source);
    void revoke(String user,String source);
    DevelopmentBaseline devBaseline(String user);
}
