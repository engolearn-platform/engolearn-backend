package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.service.TopicSpeakingOpenability.ReleasedScope;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Exact, immutable-source catalog for the resources in a published Speaking scope. */
final class TopicSpeakingResourceCatalog {
    private TopicSpeakingResourceCatalog() {}

    static Catalog build(ReleasedScope release) {
        return forItems(release == null ? List.of() : release.resourceItems());
    }

    static Catalog forItems(List<TopicItemVersion> items) {
        Map<String, TopicSpeakingResourceDto> resources = new LinkedHashMap<>();
        List<Group> groups = new ArrayList<>();
        Map<String, List<String>> legacyCandidates = new LinkedHashMap<>();
        for (TopicItemVersion item : safe(items)) {
            if (item == null) continue;
            String itemId = item.getTopicItemId();
            List<String> resourceIds = new ArrayList<>();
            Map<String, List<String>> vocabularyContexts = new LinkedHashMap<>();
            for (ItemWordVo word : safe(item.getVocabularies())) {
                if (word == null || word.getSnapshot() == null || blank(word.getSnapshot().getWord())) continue;
                WordSnapshotVo snapshot = word.getSnapshot();
                String id = vocabularyId(itemId, word, snapshot);
                if (id == null) continue;
                String detail = join(" · ", snapshot.getSense(), snapshot.getPos(), snapshot.getPhonetic(), snapshot.getDescribeVi());
                String linkedDetail = join(" — ", snapshot.getWord(), snapshot.getSense(), snapshot.getPos(), snapshot.getDescribeVi());
                put(resources, resourceIds, new TopicSpeakingResourceDto(id,
                        TopicSpeakingResourceDto.Type.VOCABULARY, snapshot.getWord(), itemId, detail));
                if (!blank(word.getVocabId())) {
                    vocabularyContexts.computeIfAbsent(word.getVocabId(), ignored -> new ArrayList<>()).add(linkedDetail);
                    addCandidate(legacyCandidates, word.getVocabId(), id);
                }
            }
            vocabularyContexts.replaceAll((key, values) -> values.stream().distinct().sorted().toList());
            addExpressions(item, resources, resourceIds, legacyCandidates, vocabularyContexts);
            groups.add(new Group(itemId, item.getTitle(), List.copyOf(resourceIds)));
        }
        return new Catalog(resources, groups, legacyCandidates);
    }

    private static void addExpressions(TopicItemVersion item, Map<String, TopicSpeakingResourceDto> resources,
                                       List<String> resourceIds, Map<String, List<String>> legacyCandidates,
                                       Map<String, List<String>> vocabularyContexts) {
        String itemId = item.getTopicItemId();
        List<ExpressionOccurrence> occurrences = new ArrayList<>();
        Map<String, Integer> multiplicities = new LinkedHashMap<>();
        List<ExpressionBlockVo> blocks = safe(item.getExpressions());
        for (int block = 0; block < blocks.size(); block++) {
            ExpressionBlockVo blockValue = blocks.get(block);
            if (blockValue == null) continue;
            List<ExpressionPurposeVo> purposes = safe(blockValue.getPurposes());
            for (int purpose = 0; purpose < purposes.size(); purpose++) {
                ExpressionPurposeVo purposeValue = purposes.get(purpose);
                if (purposeValue == null) continue;
                List<ExpressionExampleVo> examples = safe(purposeValue.getExamples());
                for (int example = 0; example < examples.size(); example++) {
                    ExpressionExampleVo value = examples.get(example);
                    if (value == null || blank(value.getTextEn())) continue;
                    List<String> linkedContext = linkedVocabularyContext(value.getLinkedVocabIds(), vocabularyContexts);
                    String semantic = semanticFingerprint(itemId, blockValue.getTips(), purposeValue, value,
                            linkedContext, value.getLinkedVocabIds());
                    String detail = join(" · ", purposeValue.getPurposeEn(), purposeValue.getPurposeVi(),
                            value.getTextVi(), value.getUsageNote(), enumName(value.getType()), enumName(value.getLabel()),
                            linkedContext.isEmpty() ? null : "Linked vocabulary: " + String.join(", ", linkedContext));
                    String legacy = "expression:" + itemId + ":" + block + ":" + purpose + ":" + example;
                    occurrences.add(new ExpressionOccurrence(semantic, value.getTextEn(), detail, legacy));
                    multiplicities.merge(semantic, 1, Integer::sum);
                }
            }
        }
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        for (ExpressionOccurrence occurrence : occurrences) {
            int multiplicity = multiplicities.get(occurrence.semantic());
            int ordinal = ordinals.merge(occurrence.semantic(), 1, Integer::sum);
            String suffix = multiplicity == 1 ? "" : ":" + multiplicity + ":" + ordinal;
            String id = "spk1:e:" + segment(itemId) + ":" + occurrence.semantic() + suffix;
            put(resources, resourceIds, new TopicSpeakingResourceDto(id,
                    TopicSpeakingResourceDto.Type.EXPRESSION, occurrence.textEn(), itemId, occurrence.detail()));
            addCandidate(legacyCandidates, occurrence.legacyId(), id);
        }
    }

    private static String semanticFingerprint(String itemId, String blockTips,
                                              ExpressionPurposeVo purpose, ExpressionExampleVo example,
                                              List<String> linkedContext, List<String> linkedVocabularyIds) {
        List<String> fields = List.of("speaking-expression-v2", safe(itemId), safe(blockTips),
                enumName(purpose.getGroupType()), safe(purpose.getPurposeEn()), safe(purpose.getPurposeVi()),
                enumName(example.getType()), safe(example.getTextEn()), safe(example.getTextVi()),
                safe(example.getUsageNote()), enumName(example.getLabel()),
                String.join("\u0000", linkedContext), linkedVocabularyReferences(linkedVocabularyIds));
        return fingerprint(fields);
    }

    private static String fingerprint(List<String> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String vocabularyId(String itemId, ItemWordVo word, WordSnapshotVo snapshot) {
        if (blank(itemId)) return null;
        String headword = normalizeHeadword(snapshot.getWord());
        if (blank(word.getVocabId())) {
            var fields = List.of(headword, safe(snapshot.getPos()), safe(snapshot.getSense()), safe(snapshot.getDescribeVi()));
            return "spk1:m:" + segment(itemId) + ":" + fingerprint(fields);
        }
        return "spk1:v:" + segment(itemId) + ":" + segment(word.getVocabId()) + ":"
                + segment(blank(word.getSenseId()) ? "" : word.getSenseId()) + ":" + segment(headword);
    }

    private static String normalizeHeadword(String value) {
        return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static List<String> linkedVocabularyContext(List<String> linkedIds, Map<String, List<String>> contexts) {
        if (linkedIds == null || linkedIds.isEmpty()) return List.of();
        TreeSet<String> descriptors = new TreeSet<>();
        for (String linkedId : linkedIds) {
            if (linkedId != null) descriptors.addAll(contexts.getOrDefault(linkedId, List.of()));
        }
        return List.copyOf(descriptors);
    }

    private static String linkedVocabularyReferences(List<String> linkedIds) {
        if (linkedIds == null || linkedIds.isEmpty()) return "";
        TreeSet<String> references = new TreeSet<>();
        linkedIds.stream().filter(Objects::nonNull).forEach(references::add);
        return String.join("\u0000", references);
    }

    private static String segment(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe(value).getBytes(StandardCharsets.UTF_8));
    }

    private static void put(Map<String, TopicSpeakingResourceDto> resources, List<String> ids,
                            TopicSpeakingResourceDto resource) {
        if (resources.putIfAbsent(resource.id(), resource) == null) ids.add(resource.id());
    }

    private static void addCandidate(Map<String, List<String>> candidates, String legacyId, String resourceId) {
        List<String> ids = candidates.computeIfAbsent(legacyId, ignored -> new ArrayList<>());
        if (!ids.contains(resourceId)) ids.add(resourceId);
    }

    private static String join(String delimiter, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) if (!blank(value)) present.add(value.trim());
        return present.isEmpty() ? null : String.join(delimiter, present);
    }

    private static String enumName(Enum<?> value) { return value == null ? "" : value.name(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }

    private record ExpressionOccurrence(String semantic, String textEn, String detail, String legacyId) {}

    record Group(String itemId, String itemTitle, List<String> resourceIds) {
        Group { resourceIds = List.copyOf(resourceIds); }
    }

    record Catalog(Map<String, TopicSpeakingResourceDto> resources, List<Group> groups,
                   Map<String, List<String>> legacyCandidates) {
        Catalog {
            resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
            groups = List.copyOf(groups);
            Map<String, List<String>> candidates = new LinkedHashMap<>();
            legacyCandidates.forEach((key, value) -> candidates.put(key, List.copyOf(value)));
            legacyCandidates = Collections.unmodifiableMap(candidates);
        }
    }
}
