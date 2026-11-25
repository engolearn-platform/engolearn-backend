package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.Group;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.Groups;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.ParentSessions;

public interface SpeakingHistory {
    Groups topics(Group group, int page, int size);

    ParentSessions parent(String topicId, int page, int size);
}
