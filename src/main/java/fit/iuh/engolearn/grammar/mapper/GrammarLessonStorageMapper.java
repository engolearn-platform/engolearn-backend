package fit.iuh.engolearn.grammar.mapper;

import fit.iuh.engolearn.grammar.model.GrammarLesson;
import fit.iuh.engolearn.grammar.model.GrammarLessonDocument;

public final class GrammarLessonStorageMapper {
    private GrammarLessonStorageMapper() { }

    public static GrammarLessonDocument document(String topicId, GrammarLesson lesson) {
        return GrammarLessonDocument.builder().id(lesson.getId()).topicId(topicId).schemaVersion(2)
                .code(lesson.getCode())
                .order(lesson.getOrder())
                .orderBeforeArchive(lesson.getOrderBeforeArchive())
                .status(lesson.getStatus())
                .statusBeforeArchive(lesson.getStatusBeforeArchive())
                .archiveReason(lesson.getArchiveReason())
                .version(lesson.getVersion())
                .contentVersion(lesson.getContentVersion())
                .firstPublishedAt(lesson.getFirstPublishedAt())
                .ready(lesson.isReady())
                .draft(lesson.getDraft())
                .published(lesson.getPublished())
                .build();
    }
}
