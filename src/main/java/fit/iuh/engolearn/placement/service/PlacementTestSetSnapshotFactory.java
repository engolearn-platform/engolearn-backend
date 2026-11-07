package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.model.vo.*;
import java.util.*;

public final class PlacementTestSetSnapshotFactory {
    private static final int TOTAL = 20;
    private static final int READING = 14;

    private PlacementTestSetSnapshotFactory() {
    }

    public static PlacementTestSet assemble(String id, String code, String name, int version,
                                            PlacementTestSetStatus status, List<PlacementQuestion> questions) {
        if (id == null || id.isBlank() || code == null || code.isBlank()
                || name == null || name.isBlank() || version < 1 || status == null) {
            throw new IllegalArgumentException("Invalid Placement test set metadata");
        }
        if (questions == null || questions.size() != TOTAL) {
            throw new IllegalArgumentException("A Placement test set must contain exactly 20 questions");
        }
        List<PlacementSnapshotItemVo> items = new ArrayList<>(TOTAL);
        Map<PlacementLevel, int[]> blueprint = new HashMap<>();
        Set<String> ids = new HashSet<>();
        Set<String> b2SemanticFamilies = new HashSet<>();
        for (int i = 0; i < TOTAL; i++) {
            PlacementQuestion question = questions.get(i);
            validateQuestion(question, i, status);
            if (!ids.add(question.getId())) {
                throw new IllegalArgumentException("Duplicate Placement question " + question.getId());
            }
            PlacementSkill requiredSkill = i < READING ? PlacementSkill.READING : PlacementSkill.LISTENING;
            if (question.getSkill() != requiredSkill) {
                throw new IllegalArgumentException("Placement question " + question.getId() + " has the wrong section");
            }
            if (question.getLevel() == PlacementLevel.B2 && question.getProvenance() != null
                    && question.getProvenance().getSemanticFamilyId() != null
                    && !b2SemanticFamilies.add(question.getProvenance().getSemanticFamilyId())) {
                throw new IllegalArgumentException("Duplicate B2 semantic family "
                        + question.getProvenance().getSemanticFamilyId());
            }
            blueprint.computeIfAbsent(question.getLevel(), ignored -> new int[2])[i < READING ? 0 : 1]++;
            items.add(toSnapshotItem(question, i + 1, requiredSkill));
        }
        validateBlueprint(blueprint);
        return PlacementTestSet.builder().id(id).code(code).name(name).version(version)
                .status(status).items(List.copyOf(items)).build();
    }

    private static void validateQuestion(PlacementQuestion question, int index, PlacementTestSetStatus testSetStatus) {
        if (question == null || question.getId() == null || question.getPrompt() == null
                || question.getPrompt().isBlank() || question.getLevel() == null || question.getSkill() == null
                || question.getOptions() == null || question.getOptions().size() != 4
                || question.getCorrectOptionId() == null) {
            throw new IllegalArgumentException("Invalid Placement question at order " + (index + 1));
        }
        if (testSetStatus == PlacementTestSetStatus.PUBLISHED
                && question.getStatus() != PlacementQuestionStatus.ACTIVE) {
            throw new IllegalArgumentException("Published Placement test sets require ACTIVE questions");
        }
        if (testSetStatus == PlacementTestSetStatus.DRAFT && question.getStatus() != PlacementQuestionStatus.DRAFT
                && question.getStatus() != PlacementQuestionStatus.ACTIVE) {
            throw new IllegalArgumentException("Draft Placement test sets require DRAFT or ACTIVE questions");
        }
        Set<String> optionIds = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (PlacementQuestionOptionVo option : question.getOptions()) {
            if (option == null || option.getId() == null || !optionIds.add(option.getId())
                    || option.getLabel() == null || !labels.add(option.getLabel())
                    || option.getText() == null || option.getText().isBlank()) {
                throw new IllegalArgumentException("Invalid Placement option for " + question.getId());
            }
        }
        if (!labels.equals(Set.of("A", "B", "C", "D")) || !optionIds.contains(question.getCorrectOptionId())) {
            throw new IllegalArgumentException("Invalid Placement correct option for " + question.getId());
        }
        if (index < READING && (question.getPassage() == null || question.getPassage().isBlank())) {
            throw new IllegalArgumentException("Reading question " + question.getId() + " has no passage");
        }
        if (index >= READING) {
            if (question.getProvenance() == null || question.getProvenance().getAudioDurationSeconds() <= 0) {
                throw new IllegalArgumentException("Listening question " + question.getId() + " has no audio duration");
            }
        }
    }

    private static PlacementSnapshotItemVo toSnapshotItem(PlacementQuestion question, int order,
                                                          PlacementSkill requiredSkill) {
        List<PlacementOptionVo> options = question.getOptions().stream()
                .map(option -> new PlacementOptionVo(option.getId(), option.getLabel(), option.getText()))
                .toList();
        PlacementAudioVo audio = requiredSkill == PlacementSkill.LISTENING
                ? new PlacementAudioVo(question.getAudioUrl(), question.getProvenance().getAudioDurationSeconds())
                : null;
        String label = question.getProvenance() == null ? null : question.getProvenance().getTitle();
        return PlacementSnapshotItemVo.builder().id(question.getId()).order(order)
                .skill(question.getSkill()).level(question.getLevel()).label(label)
                .prompt(question.getPrompt()).passage(question.getPassage())
                .options(options).correctOptionId(question.getCorrectOptionId())
                .explanation(question.getExplanation()).transcript(question.getTranscript())
                .audio(audio).build();
    }

    private static void validateBlueprint(Map<PlacementLevel, int[]> blueprint) {
        for (PlacementLevel level : PlacementLevel.values()) {
            int[] count = blueprint.get(level);
            int reading = level == PlacementLevel.A1 || level == PlacementLevel.A2 ? 4 : 3;
            if (count == null || count[0] != reading || count[1] != 5 - reading) {
                throw new IllegalArgumentException("Invalid Placement blueprint at " + level);
            }
        }
    }
}
