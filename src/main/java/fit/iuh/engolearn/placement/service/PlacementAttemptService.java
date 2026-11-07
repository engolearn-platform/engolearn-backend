package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.*;

public interface PlacementAttemptService {
    boolean isKnownGuestSession(String sessionId);
    PlacementStartOutcome start(PlacementAccessContext access);
    PlacementAttemptResponse get(String attemptId, PlacementAccessContext access);
    SavePlacementAnswerResponse saveAnswer(String attemptId, String questionId, String selectedOptionId,
                                           PlacementAccessContext access);
    PlacementSubmitResponse submit(String attemptId, PlacementAccessContext access);
}
