package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Checklist;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.CreateLessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Lesson;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.LessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Topic;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.TopicInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.TopicPage;

public interface GrammarAdminService {
    TopicPage list(String search, String level, String status, int page, int size);

    Topic getTopic(String topicId);

    Topic createTopic(TopicInput input);

    Topic updateTopic(String topicId, TopicInput input);

    void deleteTopic(String topicId, long version);

    Checklist topicChecklist(String topicId);

    Topic publishTopic(String topicId, long version);

    Topic archiveTopic(String topicId, long version);

    Topic restoreTopic(String topicId, long version);

    Lesson getLesson(String topicId, String lessonId);

    Lesson createLesson(String topicId, CreateLessonInput input);

    Lesson updateLesson(String topicId, String lessonId, LessonInput input);

    void deleteLesson(String topicId, String lessonId, long version);

    Checklist lessonChecklist(String topicId, String lessonId);

    Lesson readyLesson(String topicId, String lessonId, long version, boolean confirmPublish);

    Lesson applyLesson(String topicId, String lessonId, long version);

    Lesson discardLessonDraft(String topicId, String lessonId, long version);
}
