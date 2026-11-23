package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.GrammarLearnerRequests.SubmitAnswerRequest;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.AnswerResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.LessonResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.PracticeResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.RoadmapResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.SeenResponse;
import fit.iuh.engolearn.grammar.dto.GrammarLearnerResponses.TopicDetailResponse;

public interface GrammarLearnerService {
    RoadmapResponse roadmap();

    TopicDetailResponse topic(String topicId);

    LessonResponse lesson(String topicId, String lessonId);

    SeenResponse markSeen(String topicId, String lessonId, int contentVersion);

    PracticeResponse startPractice(String topicId, String lessonId, boolean restart);

    PracticeResponse practice(String sessionId);

    AnswerResponse submitAnswer(String sessionId, SubmitAnswerRequest request);
}
