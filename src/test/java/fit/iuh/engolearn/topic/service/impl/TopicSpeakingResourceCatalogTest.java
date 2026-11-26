package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicSpeakingResourceCatalogTest {
    @Test
    void sameVocabularyReferenceInDifferentItemsHasDistinctStableIdsAndLegacyCandidates() {
        var catalog = TopicSpeakingResourceCatalog.build(release(item("item-a", "A", vocabulary("vocab", "sense", "light")),
                item("item-b", "B", vocabulary("vocab", "sense", "light"))));

        assertEquals(2, catalog.resources().size());
        assertNotEquals(catalog.groups().get(0).resourceIds().get(0), catalog.groups().get(1).resourceIds().get(0));
        assertEquals(List.of(catalog.groups().get(0).resourceIds().get(0), catalog.groups().get(1).resourceIds().get(0)),
                catalog.legacyCandidates().get("vocab"));
        assertEquals("item-a", catalog.resources().get(catalog.groups().get(0).resourceIds().get(0)).itemId());
        var reordered = TopicSpeakingResourceCatalog.build(release(item("item-b", "B reordered", vocabulary("vocab", "sense", "light")),
                item("item-a", "A reordered", vocabulary("vocab", "sense", "light"))));
        assertEquals(catalog.resources().keySet(), reordered.resources().keySet());
    }

    @Test
    void expressionIdentitySurvivesReorderingButChangesWhenSemanticContentChanges() {
        var first = TopicSpeakingResourceCatalog.build(release(item("item", "Item", expressionBlock(
                expression("Could you help?", "request", "POLITE"), expression("Would you mind?", "request", "POLITE")))));
        var reordered = TopicSpeakingResourceCatalog.build(release(item("item", "Item", expressionBlock(
                expression("Would you mind?", "request", "POLITE"), expression("Could you help?", "request", "POLITE")))));
        var edited = TopicSpeakingResourceCatalog.build(release(item("item", "Item", expressionBlock(
                expression("Could you help me?", "request", "POLITE"), expression("Would you mind?", "request", "POLITE")))));

        assertEquals(first.resources().keySet(), reordered.resources().keySet());
        String originalId = first.resources().values().stream().filter(resource -> resource.textEn().equals("Could you help?"))
                .findFirst().orElseThrow().id();
        String editedId = edited.resources().values().stream().filter(resource -> resource.textEn().equals("Could you help me?"))
                .findFirst().orElseThrow().id();
        assertNotEquals(originalId, editedId);
    }

    @Test
    void emitsEmptyGroupsAndKeepsExpressionContextDetail() {
        var catalog = TopicSpeakingResourceCatalog.build(release(item("empty", "Empty"),
                item("with-content", "With content", expressionBlock(expression("Can you help?", "asking politely", "POLITE")))));

        assertEquals(2, catalog.groups().size());
        assertTrue(catalog.groups().get(0).resourceIds().isEmpty());
        TopicSpeakingResourceDto resource = catalog.resources().values().iterator().next();
        assertEquals("with-content", resource.itemId());
        assertTrue(resource.context().contains("asking politely"));
    }

    @Test
    void vocabularyContextIncludesMeaningfulSenseDetailAndSkipsUnidentifiedVocabulary() {
        var catalog = TopicSpeakingResourceCatalog.build(release(item("item", "Item",
                vocabulary("vocab", "sense", "light"),
                ItemWordVo.builder().snapshot(WordSnapshotVo.builder().word("unidentified").build()).build())));

        assertEquals(2, catalog.resources().size());
        TopicSpeakingResourceDto resource = catalog.resources().values().iterator().next();
        assertEquals(TopicSpeakingResourceDto.Type.VOCABULARY, resource.type());
        assertTrue(resource.context().contains("a beam or source of illumination"));
        assertEquals(List.of(resource.id()), catalog.legacyCandidates().get("vocab"));
    }

    @Test
    void vocabularyWithoutSenseRetainsStableIdentityAndLegacyAlias() {
        var catalog = TopicSpeakingResourceCatalog.build(release(item("item", "Item",
                ItemWordVo.builder().vocabId("legacy-vocab").snapshot(WordSnapshotVo.builder().word("word").build()).build())));

        TopicSpeakingResourceDto resource = catalog.resources().values().iterator().next();
        assertEquals("spk1:v:aXRlbQ:bGVnYWN5LXZvY2Fi::d29yZA", resource.id());
        assertEquals(List.of(resource.id()), catalog.legacyCandidates().get("legacy-vocab"));
    }

    @Test
    void vocabularyIdentityNormalizesHeadwordButStalesWhenEnglishHeadwordChanges() {
        var original = TopicSpeakingResourceCatalog.build(release(item("item", "Item",
                vocabulary("vocab", "sense", "  Light   ray "))));
        var normalized = TopicSpeakingResourceCatalog.build(release(item("item", "Renamed item", vocabulary("vocab", "sense", "light ray"))));
        var metadataEdit = TopicSpeakingResourceCatalog.build(release(item("item", "Item", ItemWordVo.builder()
                .vocabId("vocab").senseId("sense").snapshot(WordSnapshotVo.builder().word("LIGHT RAY")
                        .sense("different meaning metadata").phonetic("different audio metadata").build()).build())));
        var headwordEdit = TopicSpeakingResourceCatalog.build(release(item("item", "Item", vocabulary("vocab", "sense", "light beam"))));

        String originalId = original.resources().values().iterator().next().id();
        assertEquals(originalId, normalized.resources().values().iterator().next().id());
        assertEquals(originalId, metadataEdit.resources().values().iterator().next().id());
        assertNotEquals(originalId, headwordEdit.resources().values().iterator().next().id());
    }

    @Test
    void expressionContextUsesLinkedVocabularyDescriptorsAndIdentityTracksTheirMeaning() {
        String linkedId = "resource-id-7012";
        var light = TopicSpeakingResourceCatalog.build(release(item("item", "Item", vocabulary(linkedId, "sense", "light"),
                expressionBlock(expressionWithLinkedVocabulary("Turn on the light.", List.of(linkedId))))));
        var lamp = TopicSpeakingResourceCatalog.build(release(item("item", "Item", vocabulary(linkedId, "sense", "lamp"),
                expressionBlock(expressionWithLinkedVocabulary("Turn on the light.", List.of(linkedId))))));

        TopicSpeakingResourceDto original = light.resources().values().stream()
                .filter(resource -> resource.type() == TopicSpeakingResourceDto.Type.EXPRESSION).findFirst().orElseThrow();
        TopicSpeakingResourceDto changed = lamp.resources().values().stream()
                .filter(resource -> resource.type() == TopicSpeakingResourceDto.Type.EXPRESSION).findFirst().orElseThrow();
        assertNotEquals(original.id(), changed.id());
        assertTrue(original.context().contains("Linked vocabulary: light"));
        assertTrue(changed.context().contains("Linked vocabulary: lamp"));
        assertFalse(original.context().contains(linkedId));
    }

    @Test
    void duplicateExpressionIdsIncludeMultiplicitySoAddingOrRemovingDuplicatesStalesOldReferences() {
        var single = TopicSpeakingResourceCatalog.build(release(item("item", "Item", expressionBlock(
                expression("Can you help?", "request", "POLITE")))));
        var duplicatePair = TopicSpeakingResourceCatalog.build(release(item("item", "Item", expressionBlock(
                expression("Can you help?", "request", "POLITE"), expression("Can you help?", "request", "POLITE")))));

        assertTrue(single.resources().keySet().stream().noneMatch(duplicatePair.resources()::containsKey));
        assertTrue(duplicatePair.resources().keySet().stream().noneMatch(single.resources()::containsKey));
        assertTrue(duplicatePair.resources().keySet().stream().allMatch(id -> id.matches(".*:2:[12]$")));
    }

    private static ReleasedScope release(TopicItemVersion... items) {
        return new ReleasedScope("scope", "topic", null, "v1", List.of(items));
    }

    private static TopicItemVersion item(String id, String title, Object... content) {
        TopicItemVersion.TopicItemVersionBuilder<?, ?> builder = TopicItemVersion.builder().topicItemId(id).title(title);
        var vocabularies = new java.util.ArrayList<ItemWordVo>();
        var expressions = new java.util.ArrayList<ExpressionBlockVo>();
        for (Object value : content) {
            if (value instanceof ItemWordVo word) {
                vocabularies.add(word);
            } else if (value instanceof ExpressionBlockVo block) {
                expressions.add(block);
            }
        }
        builder.vocabularies(vocabularies).expressions(expressions);
        return builder.build();
    }

    private static ItemWordVo vocabulary(String vocabId, String senseId, String word) {
        return ItemWordVo.builder().vocabId(vocabId).senseId(senseId)
                .snapshot(WordSnapshotVo.builder().word(word).sense("a beam or source of illumination")
                        .pos("noun").phonetic("/laɪt/").describeVi("ánh sáng").build()).build();
    }

    private static ExpressionBlockVo expressionBlock(ExpressionExampleVo... examples) {
        return ExpressionBlockVo.builder().purposes(List.of(ExpressionPurposeVo.builder().purposeEn("request")
                .purposeVi("đề nghị").examples(List.of(examples)).build())).build();
    }

    private static ExpressionExampleVo expression(String text, String usageNote, String label) {
        return ExpressionExampleVo.builder().textEn(text).textVi("Bạn có thể giúp tôi không?").usageNote(usageNote)
                .label(fit.iuh.engolearn.topic.model.enums.ExpressionLabel.valueOf(label)).build();
    }

    private static ExpressionExampleVo expressionWithLinkedVocabulary(String text, List<String> linkedVocabularyIds) {
        return ExpressionExampleVo.builder().textEn(text).textVi("Bật đèn lên.").usageNote("instruction")
                .linkedVocabIds(linkedVocabularyIds).build();
    }
}
