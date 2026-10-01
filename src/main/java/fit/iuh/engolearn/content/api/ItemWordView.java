package fit.iuh.engolearn.content.api;

public record ItemWordView(
        String vocabId,
        String senseId,
        String word,
        String pos,
        String meaningVi,
        String phonetic,
        String audioUrl,
        String tips,
        Integer orderIndex) {
}
