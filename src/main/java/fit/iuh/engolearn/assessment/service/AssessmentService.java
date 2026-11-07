package fit.iuh.engolearn.assessment.service;
import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import java.security.*;
import java.time.*;
import java.util.*;
public interface AssessmentService {
    AssessmentPreparationResponse preparation(String user);
    AssessmentStateResponse start(String user,AssessmentStartRequest command);
    AssessmentStateResponse state(String user,String id,String binding);
    AssessmentStateResponse answer(String user,String id,String binding,AssessmentAnswerRequest request,boolean advance);
    AssessmentStateResponse finishStage(String user,String id,String binding,AssessmentFinishStageRequest request);
    AssessmentStateResponse submit(String user,String id,String binding);
    AssessmentStateResponse abandon(String user,String id);
    AssessmentMediaResponse audio(String user,String id,String binding,AssessmentAudioRequest command);
    AssessmentMediaResponse reviewAudio(String user,String id,int index);
    List<AssessmentHistoryItemResponse> history(String user,int page,int size);
    AssessmentResultResponse result(String user,String id);
    AssessmentReviewResponse review(String user,String id);
    AssessmentMediaResponse soundCheck();
    AssessmentStateResponse reportAudioFailure(String user,String id,String binding,AssessmentAudioFailureRequest request);
    void expire(String user,String id);
}
