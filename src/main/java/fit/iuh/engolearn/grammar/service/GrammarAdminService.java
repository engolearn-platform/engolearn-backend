package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.response.GrammarChecklistResponse;
import fit.iuh.engolearn.grammar.dto.request.CreateGrammarLessonRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementLessonResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarLessonRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementTopicResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarTopicRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarTopicPageResponse;

public interface GrammarAdminService {
    GrammarTopicPageResponse list(String search, String level, String status, int page, int size);

    GrammarManagementTopicResponse getTopic(String topicId);

    GrammarManagementTopicResponse createTopic(GrammarTopicRequest input);

    GrammarManagementTopicResponse updateTopic(String topicId, GrammarTopicRequest input);

    void deleteTopic(String topicId, long version);

    GrammarChecklistResponse topicChecklist(String topicId);

    GrammarManagementTopicResponse publishTopic(String topicId, long version);

    GrammarManagementTopicResponse archiveTopic(String topicId, long version);

    GrammarManagementTopicResponse restoreTopic(String topicId, long version);

    GrammarManagementLessonResponse getLesson(String topicId, String lessonId);

    GrammarManagementLessonResponse createLesson(String topicId, CreateGrammarLessonRequest input);

    GrammarManagementLessonResponse updateLesson(String topicId, String lessonId, GrammarLessonRequest input);

    void deleteLesson(String topicId, String lessonId, long version);

    GrammarChecklistResponse lessonChecklist(String topicId, String lessonId);

    GrammarManagementLessonResponse readyLesson(String topicId, String lessonId, long version, boolean confirmPublish);

    GrammarManagementLessonResponse applyLesson(String topicId, String lessonId, long version);

    GrammarManagementLessonResponse discardLessonDraft(String topicId, String lessonId, long version);
}
