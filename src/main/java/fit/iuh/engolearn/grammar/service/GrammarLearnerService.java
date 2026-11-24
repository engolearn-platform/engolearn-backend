package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.request.GrammarSubmitAnswerRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarAnswerResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarLessonResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarPracticeResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarRoadmapResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarSeenResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicDetailResponse;

public interface GrammarLearnerService {
    GrammarRoadmapResponse roadmap();

    GrammarTopicDetailResponse topic(String topicId);

    GrammarLessonResponse lesson(String topicId, String lessonId);

    GrammarSeenResponse markSeen(String topicId, String lessonId, int contentVersion);

    GrammarPracticeResponse startPractice(String topicId, String lessonId, boolean restart);

    GrammarPracticeResponse practice(String sessionId);

    GrammarAnswerResponse submitAnswer(String sessionId, GrammarSubmitAnswerRequest request);
}
