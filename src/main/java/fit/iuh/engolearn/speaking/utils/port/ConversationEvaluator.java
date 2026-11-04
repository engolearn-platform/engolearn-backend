package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;

import java.util.List;

public interface ConversationEvaluator {
    record Exchange(String learner, String ai) {}
    record Result(boolean valid, String reply,
                  List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
                  List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
                  List<SpeakingAssessmentRecord.Correction> corrections,
                  String hint, List<String> feedback) {}

    Result evaluate(SpeakingContentSnapshot content, String transcript,
                    List<Exchange> history, List<String> achievedGoalIds);
}
