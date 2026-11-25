package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.*;
import fit.iuh.engolearn.speaking.dto.response.SpeakingAssessmentResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;

public interface SpeakingSessions {
    Availability availability(SpeakingScopeDto scope);
    StartResult create(Create request);
    StartResult replace(Replace request);
    Session read(String id);
    Session resume(String id, Resume request);
    Ticket ticket(String id, long epoch);
    Session end(String id, long epoch);
    Page<Session> history(SpeakingScopeDto scope, int page, int size);
    Page<Turn> turns(String id, int page, int size);
    SpeakingAssessmentResponse assessment(String id);
    void delete(String id);
    void deleteAll();
    Audio turnAudio(String id, String turnId, String kind);
    Audio openingAudio(String id);

    record StartResult(Session session, boolean created, String replacingSessionId) {}
}
