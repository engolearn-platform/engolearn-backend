package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.DialogueAct;
import fit.iuh.engolearn.speaking.model.SpeakingAiDraftResponseV1;
import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SpeakingDraftValidator {
    private static final String CONTRACT_VERSION = "speaking.ai.draft.v1";
    private static final Set<String> ROOT_FIELDS = Set.of("contractVersion", "assessment", "dialogue");
    private static final Set<String> ASSESSMENT_FIELDS = Set.of("turnValidityProposal", "userIntent", "goalEvidence",
            "resourceEvidence", "corrections", "hintVi");
    private static final Set<String> DIALOGUE_FIELDS = Set.of("proposedAct", "spokenReply", "continuationType",
            "primaryQuestion", "targetGoalId");

    private final ObjectMapper mapper;

    public SpeakingDraftValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SpeakingAiDraftResponseV1 validateSchema(String rawJson) {
        final JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception exception) {
            throw new SpeakingDraftValidationException("Draft is not valid JSON", exception);
        }
        requireObject(root, "root");
        requireFields(root, ROOT_FIELDS, "root");
        requireText(root, "contractVersion", "root");
        if (!CONTRACT_VERSION.equals(root.path("contractVersion").asText())) {
            throw invalid("Unsupported contractVersion");
        }

        JsonNode assessment = root.path("assessment");
        requireObject(assessment, "assessment");
        requireFields(assessment, ASSESSMENT_FIELDS, "assessment");
        SpeakingAiDraftResponseV1.TurnValidity validity = enumValue(assessment.path("turnValidityProposal"),
                SpeakingAiDraftResponseV1.TurnValidity.values(), "turnValidityProposal");
        SpeakingAiDraftResponseV1.UserIntent intent = enumValue(assessment.path("userIntent"),
                SpeakingAiDraftResponseV1.UserIntent.values(), "userIntent");
        List<SpeakingAiDraftResponseV1.GoalEvidence> goals = goalEvidence(assessment.path("goalEvidence"));
        List<SpeakingAiDraftResponseV1.ResourceEvidence> resources = resourceEvidence(assessment.path("resourceEvidence"));
        List<SpeakingAiDraftResponseV1.Correction> corrections = corrections(assessment.path("corrections"));
        String hint = nullableText(assessment.path("hintVi"), "hintVi");

        JsonNode dialogueNode = root.get("dialogue");
        SpeakingAiDraftResponseV1.Dialogue dialogue = null;
        if (validity == SpeakingAiDraftResponseV1.TurnValidity.VALID) {
            if (dialogueNode == null || !dialogueNode.isObject()) throw invalid("VALID requires dialogue");
            dialogue = dialogue(dialogueNode);
        } else {
            if (dialogueNode == null || !dialogueNode.isNull()) throw invalid("INVALID/UNCERTAIN require null dialogue");
            if (!goals.isEmpty() || !resources.isEmpty() || !corrections.isEmpty() || hint != null) {
                throw invalid("INVALID/UNCERTAIN cannot contain assessment evidence");
            }
        }
        return new SpeakingAiDraftResponseV1(CONTRACT_VERSION,
                new SpeakingAiDraftResponseV1.Assessment(validity, intent, goals, resources, corrections, hint), dialogue);
    }

    public ValidatedTurnProposal validate(String rawJson, ValidationContext context) {
        return validateInternal(validateSchema(rawJson), context, false);
    }

    private ValidatedTurnProposal validateInternal(SpeakingAiDraftResponseV1 draft, ValidationContext context,
            boolean trustV2GoalAssessment) {
        if (draft.assessment().turnValidityProposal() == SpeakingAiDraftResponseV1.TurnValidity.INVALID) {
            return ValidatedTurnProposal.invalid();
        }
        if (draft.assessment().turnValidityProposal() == SpeakingAiDraftResponseV1.TurnValidity.UNCERTAIN) {
            return ValidatedTurnProposal.evaluationFailed();
        }
        Set<String> goalIds = new HashSet<>();
        List<SpeakingAssessmentRecord.GoalEvidence> acceptedGoals = new ArrayList<>();
        for (SpeakingAiDraftResponseV1.GoalEvidence evidence : draft.assessment().goalEvidence()) {
            SpeakingGoal goal = context.snapshot().goals().stream()
                    .filter(candidate -> candidate.id().equals(evidence.goalId()))
                    .findFirst().orElse(null);
            if (goal != null && !context.achievedGoalIds().contains(goal.id())
                    && goalIds.add(goal.id()) && (trustV2GoalAssessment
                    || semanticallySupportsGoal(evidence, goal, context.currentTranscript()))) {
                acceptedGoals.add(new SpeakingAssessmentRecord.GoalEvidence(evidence.goalId(), evidence.quote()));
            }
        }

        Set<String> resourceKeys = new HashSet<>();
        List<SpeakingAssessmentRecord.ResourceEvidence> acceptedResources = new ArrayList<>();
        for (SpeakingAiDraftResponseV1.ResourceEvidence evidence : draft.assessment().resourceEvidence()) {
            PinnedResource resource = context.resources().get(evidence.resourceId());
            String key = evidence.resourceId() + "\u0000" + evidence.resourceKind();
            if (resource != null && resource.kind().equals(evidence.resourceKind().name())
                    && resourceKeys.add(key) && contains(context.currentTranscript(), evidence.quote())
                    && contains(resource.text(), evidence.quote()) && !isNegatedOrQuoted(context.currentTranscript(), evidence.quote())) {
                acceptedResources.add(new SpeakingAssessmentRecord.ResourceEvidence(
                        evidence.resourceId(), evidence.quote(), null));
            }
        }

        List<SpeakingAssessmentRecord.Correction> acceptedCorrections = draft.assessment().corrections().stream()
                .filter(correction -> contains(context.currentTranscript(), correction.original()))
                .filter(correction -> !meaningBreaking(correction.original(), correction.suggestedEnglish()))
                .filter(correction -> !likelySttSubstitution(correction.original(), correction.suggestedEnglish()))
                .map(correction -> new SpeakingAssessmentRecord.Correction(correction.original(),
                        correction.suggestedEnglish(), correction.explanationVi())).limit(1).toList();

        var dialogue = draft.dialogue();
        String targetGoalId = dialogue == null ? null : dialogue.targetGoalId();
        boolean invalidTarget = targetGoalId != null && (context.snapshot().goals().stream()
                .noneMatch(goal -> goal.id().equals(targetGoalId)) || context.achievedGoalIds().contains(targetGoalId));
        if (dialogue != null && (dialogue.proposedAct() == DialogueAct.WRAP_UP || invalidTarget)) {
            dialogue = null;
        }
        return new ValidatedTurnProposal(ValidatedTurnProposal.ValidityOutcome.VALID,
                List.copyOf(acceptedGoals), List.copyOf(acceptedResources), acceptedCorrections,
                draft.assessment().hintVi(), dialogue, draft.assessment().userIntent(), List.of(), null, null,
                "speaking-v1");
    }

    public ValidatedTurnProposal validateV2(String rawJson, ValidationContext context) {
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception exception) {
            throw new SpeakingDraftValidationException("Draft is not valid JSON", exception);
        }
        requireObject(root, "root");
        requireFields(root, ROOT_FIELDS, "root");
        requireText(root, "contractVersion", "root");
        if (!"speaking.ai.draft.v2".equals(root.path("contractVersion").asText())) {
            throw invalid("Unsupported contractVersion");
        }
        JsonNode assessment = root.path("assessment");
        requireObject(assessment, "assessment");
        Set<String> v2Fields = Set.of("turnValidityProposal", "userIntent", "goalEvidence", "resourceEvidence",
                "corrections", "hintExampleEn", "hintLeadVi");
        requireFields(assessment, v2Fields, "assessment");
        String hintExampleEn = nullableText(assessment.path("hintExampleEn"), "hintExampleEn");
        String hintLeadVi = nullableText(assessment.path("hintLeadVi"), "hintLeadVi");
        String normalized = Pattern.compile(",\\s*\\\"hintLeadVi\\\"\\s*:\\s*(?:null|\\\"(?:\\\\.|[^\\\"\\\\])*\\\")")
                .matcher(rawJson).replaceFirst("");
        normalized = normalized.replace("speaking.ai.draft.v2", "speaking.ai.draft.v1")
                .replace("hintExampleEn", "hintVi");
        ValidatedTurnProposal proposal = validateInternal(validateSchema(normalized), context, true);
        if (proposal.userIntent() == SpeakingAiDraftResponseV1.UserIntent.CLOSING_UTTERANCE) {
            hintExampleEn = null;
            hintLeadVi = null;
        }
        return new ValidatedTurnProposal(proposal.validityOutcome(), proposal.goalEvidence(), proposal.resourceEvidence(),
                proposal.corrections(), proposal.hint(), proposal.dialogue(), proposal.userIntent(),
                proposal.validationReasons(), hintExampleEn, hintLeadVi, "speaking-v2");
    }

    private SpeakingAiDraftResponseV1.Dialogue dialogue(JsonNode node) {
        requireFields(node, DIALOGUE_FIELDS, "dialogue");
        DialogueAct act = enumValue(node.path("proposedAct"), DialogueAct.values(), "proposedAct");
        String reply = requiredText(node, "spokenReply", "dialogue");
        SpeakingAiDraftResponseV1.ContinuationType continuation = enumValue(node.path("continuationType"),
                SpeakingAiDraftResponseV1.ContinuationType.values(), "continuationType");
        String question = nullableText(node.path("primaryQuestion"), "primaryQuestion");
        String target = nullableText(node.path("targetGoalId"), "targetGoalId");
        switch (act) {
            case GUIDE_GOAL -> { if (target == null) throw invalid("GUIDE_GOAL requires targetGoalId"); }
            case WRAP_UP -> { if (continuation != SpeakingAiDraftResponseV1.ContinuationType.NONE || question != null || target != null) throw invalid("WRAP_UP cannot continue"); }
            case REACT_AND_ASK -> { if (continuation != SpeakingAiDraftResponseV1.ContinuationType.QUESTION || question == null) throw invalid("REACT_AND_ASK requires a question"); }
            case REACT_AND_INVITE -> { if (continuation != SpeakingAiDraftResponseV1.ContinuationType.INVITATION && continuation != SpeakingAiDraftResponseV1.ContinuationType.ACTION_PROMPT) throw invalid("REACT_AND_INVITE requires invitation"); }
            case CLARIFY -> { if (continuation != SpeakingAiDraftResponseV1.ContinuationType.QUESTION && continuation != SpeakingAiDraftResponseV1.ContinuationType.CONFIRMATION) throw invalid("CLARIFY requires question or confirmation"); }
            case ANSWER_AND_ASK, ADVANCE_SCENARIO -> { if (continuation == SpeakingAiDraftResponseV1.ContinuationType.NONE) throw invalid("Act requires continuation"); }
        }
        return new SpeakingAiDraftResponseV1.Dialogue(act, reply, continuation, question, target);
    }

    private List<SpeakingAiDraftResponseV1.GoalEvidence> goalEvidence(JsonNode node) {
        return array(node, "goalEvidence").stream().map(item -> {
            requireFields(item, Set.of("goalId", "quote"), "goalEvidence item");
            return new SpeakingAiDraftResponseV1.GoalEvidence(requiredText(item, "goalId", "goalEvidence item"), requiredText(item, "quote", "goalEvidence item"));
        }).toList();
    }

    private List<SpeakingAiDraftResponseV1.ResourceEvidence> resourceEvidence(JsonNode node) {
        return array(node, "resourceEvidence").stream().map(item -> {
            requireFields(item, Set.of("resourceId", "resourceKind", "quote"), "resourceEvidence item");
            return new SpeakingAiDraftResponseV1.ResourceEvidence(requiredText(item, "resourceId", "resourceEvidence item"),
                    enumValue(item.path("resourceKind"), SpeakingAiDraftResponseV1.ResourceKind.values(), "resourceKind"), requiredText(item, "quote", "resourceEvidence item"));
        }).toList();
    }

    private List<SpeakingAiDraftResponseV1.Correction> corrections(JsonNode node) {
        return array(node, "corrections").stream().map(item -> {
            requireFields(item, Set.of("original", "suggestedEnglish", "explanationVi"), "correction item");
            return new SpeakingAiDraftResponseV1.Correction(requiredText(item, "original", "correction item"),
                    requiredText(item, "suggestedEnglish", "correction item"), requiredText(item, "explanationVi", "correction item"));
        }).toList();
    }

    private List<JsonNode> array(JsonNode node, String name) {
        if (node == null || !node.isArray()) throw invalid(name + " must be an array");
        List<JsonNode> values = new ArrayList<>();
        node.forEach(value -> { if (!value.isObject()) throw invalid(name + " items must be objects"); values.add(value); });
        return values;
    }

    private boolean semanticallySupportsGoal(SpeakingAiDraftResponseV1.GoalEvidence evidence, SpeakingGoal goal, String transcript) {
        if (!contains(transcript, evidence.quote()) || isNegatedOrQuoted(transcript, evidence.quote())) return false;
        String[] conditions = goal.description().toLowerCase(Locale.ROOT).split("\\s+and\\s+");
        return StreamSupport.all(conditions, condition -> condition.isBlank() || contains(transcript, condition.trim()));
    }

    private boolean meaningBreaking(String original, String suggested) {
        return negated(original) != negated(suggested);
    }

    private boolean likelySttSubstitution(String original, String suggested) {
        String left = normalized(original);
        String right = normalized(suggested);
        if (left.equals(right)) return true;
        if (left.contains(" ") || right.contains(" ")) return false;
        return editDistanceAtMostOne(left, right);
    }

    private boolean editDistanceAtMostOne(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) return false;
        int differences = 0;
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) { i++; j++; continue; }
            if (++differences > 1) return false;
            if (left.length() > right.length()) i++;
            else if (right.length() > left.length()) j++;
            else { i++; j++; }
        }
        return differences + (left.length() - i) + (right.length() - j) <= 1;
    }

    private boolean isNegatedOrQuoted(String text, String quote) {
        if (negated(quote)) return true;
        int index = text.toLowerCase(Locale.ROOT).indexOf(quote.toLowerCase(Locale.ROOT));
        if (index < 0) return true;
        String before = normalized(text.substring(0, index));
        String window = before.substring(Math.max(0, before.length() - 24));
        String surrounding = text.substring(Math.max(0, index - 1), Math.min(text.length(), index + quote.length() + 2));
        return negated(window) || quote.contains("?") || surrounding.matches(".*[\\\"'].*")
                || text.indexOf('?', index + quote.length()) >= 0;
    }

    private boolean negated(String text) {
        String value = normalized(text);
        return value.contains("don't") || value.contains("do not") || value.contains("not ") || value.contains("never") || value.contains("no ");
    }

    private boolean contains(String text, String value) {
        return normalized(text).contains(normalized(value));
    }

    private String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void requireFields(JsonNode node, Set<String> expected, String name) {
        Set<String> actual = new HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(expected)) throw invalid(name + " contains unknown or missing fields");
    }

    private void requireObject(JsonNode node, String name) { if (node == null || !node.isObject()) throw invalid(name + " must be an object"); }
    private void requireText(JsonNode node, String field, String name) { if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw invalid(name + "." + field + " must be nonblank text"); }
    private String requiredText(JsonNode node, String field, String name) { requireText(node, field, name); return node.path(field).asText(); }
    private String nullableText(JsonNode node, String name) { if (node == null || node.isNull()) return null; if (!node.isTextual() || node.asText().isBlank()) throw invalid(name + " must be text or null"); return node.asText(); }

    private <T extends Enum<T>> T enumValue(JsonNode node, T[] values, String name) {
        if (node == null || !node.isTextual()) throw invalid(name + " must be an enum value");
        for (T value : values) if (value.name().equals(node.asText())) return value;
        throw invalid("Unknown " + name);
    }

    private static SpeakingDraftValidationException invalid(String message) { return new SpeakingDraftValidationException(message); }

    public record PinnedResource(String id, String kind, String text) {}
    public record ValidationContext(String currentTranscript, SpeakingContentSnapshot snapshot,
                                    Map<String, PinnedResource> resources, List<String> achievedGoalIds) {}

    private static final class StreamSupport {
        private static boolean all(String[] values, Predicate<String> predicate) {
            for (String value : values) if (!predicate.test(value)) return false;
            return true;
        }
    }
}
