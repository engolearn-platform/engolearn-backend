package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.model.DialogueAct;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.GatewayOutcome;
import fit.iuh.engolearn.speaking.model.SpeakingAiRequestV1;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingConversationAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeakingConversationComposerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TRANSCRIPT = "I want a coffee.";

    @Test
    void validDraftUsesPinnedRequestHistoryAndAcceptedEvidenceThenAuthorizesReply() {
        AtomicInteger calls = new AtomicInteger();
        List<SpeakingAiRequestV1> requests = new ArrayList<>();
        SpeakingAiGateway gateway = request -> {
            calls.incrementAndGet();
            requests.add(request);
            return success(validDraft("STATEMENT", "REACT_AND_ASK", "What size would you like?",
                    "QUESTION", "What size would you like?", "goal-coffee", "coffee", "vocabulary:0", "VOCABULARY", "coffee",
                    "expression:0", "EXPRESSION", "I want"));
        };
        SpeakingSession session = session(activeSnapshot());

        SpeakingConversationAssessment assessment = composer(gateway).assess(session,
                historySnapshot(session, sixSuccessfulTurns()), TRANSCRIPT);

        assertEquals(1, calls.get());
        assertEquals(ValidatedTurnProposal.ValidityOutcome.VALID, assessment.proposal().validityOutcome());
        assertEquals(List.of("goal-coffee"), assessment.proposal().goalEvidence().stream()
                .map(evidence -> evidence.goalId()).toList());
        assertEquals(DialogueDecision.Outcome.NORMAL, assessment.decision().outcome());
        assertEquals(DialogueDecision.ReasonCode.NORMAL, assessment.decision().reasonCode());
        assertEquals(DialogueAct.REACT_AND_ASK, assessment.decision().acceptedAct());
        assertEquals("What size would you like?", assessment.decision().spokenReplyToDeliver());
        assertEquals(List.of("goal-coffee"), assessment.decision().newlyAchievedGoalIds());
        assertEquals(List.of(), session.getAchievedGoalIds());
        assertEquals(2, session.getValidTurnCount());

        SpeakingAiRequestV1 request = requests.get(0);
        assertEquals("session-title", request.pinnedSnapshot().title());
        assertEquals(List.of("goal-coffee", "goal-size"), request.pinnedSnapshot().goals().stream()
                .map(SpeakingAiRequestV1.PinnedGoal::id).toList());
        assertEquals(List.of("vocabulary:0", "expression:0"), request.resources().stream()
                .map(SpeakingAiRequestV1.PinnedResource::id).toList());
        assertEquals(List.of("VOCABULARY", "EXPRESSION"), request.resources().stream()
                .map(SpeakingAiRequestV1.PinnedResource::kind).toList());
        assertEquals(List.of("coffee", "I want"), request.resources().stream()
                .map(SpeakingAiRequestV1.PinnedResource::text).toList());
        assertEquals(10, request.recentHistory().size());
        assertEquals("learner-2", request.recentHistory().get(0).text());
        assertEquals("partner-2", request.recentHistory().get(1).text());
        assertEquals("learner-6", request.recentHistory().get(8).text());
        assertEquals("partner-6", request.recentHistory().get(9).text());
        assertEquals(TRANSCRIPT, request.currentFinalizedTranscript());
    }

    @Test
    void invalidAndUncertainDraftsRemainDistinctAndDoNotDeliverSpeech() {
        assertOutcome(invalidDraft(), ValidatedTurnProposal.ValidityOutcome.INVALID,
                DialogueDecision.Outcome.RECOVERY, DialogueDecision.ReasonCode.INVALID_TURN);
        assertOutcome(invalidDraft().replace("INVALID", "UNCERTAIN"),
                ValidatedTurnProposal.ValidityOutcome.EVALUATION_FAILED,
                DialogueDecision.Outcome.RECOVERY, DialogueDecision.ReasonCode.EVALUATION_ERROR);
    }

    @Test
    void providerAndSchemaFailuresBecomeEvaluationFailures() {
        AtomicInteger calls = new AtomicInteger();
        SpeakingAiGateway gateway = request -> {
            calls.incrementAndGet();
            return new GatewayOutcome.HttpFailure(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.SERVER_5XX, "provider unavailable", 503, null, null));
        };

        SpeakingConversationAssessment providerFailure = composer(gateway).assess(session(activeSnapshot()),
                emptyHistory(), TRANSCRIPT);
        assertEquals(ValidatedTurnProposal.ValidityOutcome.EVALUATION_FAILED,
                providerFailure.proposal().validityOutcome());
        assertEquals(DialogueDecision.ReasonCode.EVALUATION_ERROR, providerFailure.decision().reasonCode());
        assertNull(providerFailure.decision().spokenReplyToDeliver());

        SpeakingConversationAssessment schemaFailure = composer(request -> {
            calls.incrementAndGet();
            return new GatewayOutcome.Success(newRaw("{}"));
        }).assess(session(activeSnapshot()), emptyHistory(), TRANSCRIPT);
        assertEquals(ValidatedTurnProposal.ValidityOutcome.EVALUATION_FAILED,
                schemaFailure.proposal().validityOutcome());
        assertEquals(DialogueDecision.ReasonCode.EVALUATION_ERROR, schemaFailure.decision().reasonCode());
        assertNull(schemaFailure.decision().spokenReplyToDeliver());
        assertEquals(2, calls.get());
    }

    @Test
    void managerSuppressesWrapUpAnswerFirstAlreadyAchievedAndTerminalReplies() {
        SpeakingSession session = session(activeSnapshot());

        SpeakingConversationAssessment wrapUp = composer(successGateway(wrapUpDraft())).assess(session, emptyHistory(), TRANSCRIPT);
        assertEquals(DialogueDecision.ReasonCode.DIALOGUE_NOT_AVAILABLE, wrapUp.decision().reasonCode());
        assertNull(wrapUp.decision().spokenReplyToDeliver());

        SpeakingConversationAssessment answerFirst = composer(successGateway(validDraft("QUESTION", "GUIDE_GOAL",
                "Would you like coffee?", "QUESTION", "goal-coffee", "goal-coffee", "coffee", null, null, null, null, null, null)))
                .assess(session, emptyHistory(), "What do you recommend?");
        assertEquals(DialogueDecision.ReasonCode.CLARIFICATION_FALLBACK, answerFirst.decision().reasonCode());
        assertEquals(DialogueAct.CLARIFY, answerFirst.decision().acceptedAct());
        assertEquals("Could you clarify what you mean?", answerFirst.decision().spokenReplyToDeliver());

        SpeakingConversationAssessment achievedTarget = composer(successGateway(validDraft("STATEMENT", "GUIDE_GOAL",
                "Would you like coffee?", "QUESTION", "goal-coffee", "goal-coffee", "coffee", null, null, null,
                null, null, null))).assess(session, emptyHistory(), TRANSCRIPT);
        assertEquals(DialogueDecision.ReasonCode.TARGET_ALREADY_ACHIEVED, achievedTarget.decision().reasonCode());
        assertNull(achievedTarget.decision().spokenReplyToDeliver());

        SpeakingSession terminal = session(activeSnapshot());
        terminal.setStatus(SpeakingSession.Status.COMPLETED);
        SpeakingConversationAssessment terminalAssessment = composer(successGateway(validDraft("STATEMENT", "REACT_AND_ASK",
                "Should I continue?", "QUESTION", "Should I continue?", null, null, null, null, null, null, null, null)))
                .assess(terminal, emptyHistory(), TRANSCRIPT);
        assertEquals(DialogueDecision.Outcome.REJECT, terminalAssessment.decision().outcome());
        assertEquals(DialogueDecision.ReasonCode.REJECTED_TERMINAL, terminalAssessment.decision().reasonCode());
        assertNull(terminalAssessment.decision().spokenReplyToDeliver());
    }

    private static void assertOutcome(String rawJson, ValidatedTurnProposal.ValidityOutcome validity,
            DialogueDecision.Outcome outcome, DialogueDecision.ReasonCode reason) {
        AtomicInteger calls = new AtomicInteger();
        SpeakingConversationAssessment assessment = composer(request -> {
            calls.incrementAndGet();
            return new GatewayOutcome.Success(newRaw(rawJson));
        }).assess(session(activeSnapshot()), emptyHistory(), TRANSCRIPT);

        assertEquals(1, calls.get());
        assertEquals(validity, assessment.proposal().validityOutcome());
        assertEquals(outcome, assessment.decision().outcome());
        assertEquals(reason, assessment.decision().reasonCode());
        assertNull(assessment.decision().spokenReplyToDeliver());
    }

    private static SpeakingConversationComposer composer(SpeakingAiGateway gateway) {
        return new SpeakingConversationComposer(gateway, new SpeakingDraftValidator(MAPPER), new ConversationManager());
    }

    private static SpeakingAiGateway successGateway(String rawJson) {
        return request -> new GatewayOutcome.Success(newRaw(rawJson));
    }

    private static GatewayOutcome.Success success(String rawJson) {
        return new GatewayOutcome.Success(newRaw(rawJson));
    }

    private static fit.iuh.engolearn.speaking.model.ProviderRawDraft newRaw(String rawJson) {
        return new fit.iuh.engolearn.speaking.model.ProviderRawDraft(rawJson,
                new fit.iuh.engolearn.speaking.model.ProviderRawDraft.ProviderMetadata(
                        "test-model", 200, "response-1", "STOP", 1L, 1L, 2L));
    }

    private static String invalidDraft() {
        return validDraft("INVALID", null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String wrapUpDraft() {
        return validDraft("STATEMENT", "WRAP_UP", "Thank you for visiting.", "NONE", null,
                null, null, null, null, null, null, null, null);
    }

    private static String validDraft(String intent, String act, String reply, String continuation,
            String question, String goalId, String goalQuote, String resourceId, String resourceKind,
            String resourceQuote, String unusedResourceId, String unusedResourceKind, String unusedResourceQuote) {
        String goalEvidence = goalId == null ? "[]" : "[{\"goalId\":\"" + goalId + "\",\"quote\":\""
                + goalQuote + "\"}]";
        String resourceEvidence = resourceId == null ? "[]" : "[{\"resourceId\":\"" + resourceId
                + "\",\"resourceKind\":\"" + resourceKind + "\",\"quote\":\"" + resourceQuote + "\"}"
                + (unusedResourceId == null ? "" : ",{\"resourceId\":\"" + unusedResourceId
                + "\",\"resourceKind\":\"" + unusedResourceKind + "\",\"quote\":\""
                + unusedResourceQuote + "\"}") + "]";
        String dialogue = act == null ? "null" : "{\"proposedAct\":\"" + act + "\",\"spokenReply\":\""
                + reply + "\",\"continuationType\":\"" + continuation + "\",\"primaryQuestion\":"
                + (question == null ? "null" : "\"" + question + "\"") + ",\"targetGoalId\":"
                + (goalId == null ? "null" : "\"" + goalId + "\"") + "}";
        return "{\"contractVersion\":\"speaking.ai.draft.v1\",\"assessment\":{"
                + "\"turnValidityProposal\":\"" + (intent.equals("INVALID") || intent.equals("UNCERTAIN")
                ? intent : "VALID") + "\","
                + "\"userIntent\":\"" + (intent.equals("INVALID") || intent.equals("UNCERTAIN") ? "OTHER" : intent)
                + "\",\"goalEvidence\":" + goalEvidence
                + ",\"resourceEvidence\":" + resourceEvidence
                + ",\"corrections\":[],\"hintVi\":null},\"dialogue\":" + dialogue + "}";
    }

    private static SpeakingSession session(SpeakingContentSnapshot snapshot) {
        return SpeakingSession.builder().id("session-1").learnerId("learner-1").status(SpeakingSession.Status.ACTIVE)
                .content(snapshot).achievedGoalIds(new ArrayList<>()).committedTurnCount(2).validTurnCount(2)
                .controllerEpoch(1L).revision(1L).build();
    }

    private static SpeakingContentSnapshot activeSnapshot() {
        return new SpeakingContentSnapshot("item-1", "version-1", "session-title", "Order a coffee",
                List.of(new SpeakingGoal("goal-coffee", "Order coffee", "coffee", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("goal-size", "Ask size", "size", SpeakingGoal.Priority.OPTIONAL)),
                null, 5, List.of("coffee"), List.of("I want"), 7L, "REQUIRED_GOALS_V2");
    }

    private static SpeakingSessionResponse historySnapshot(SpeakingSession session, List<SpeakingSessionResponse.Turn> turns) {
        return new SpeakingSessionResponse("session-1", session.getContent(), SpeakingSession.Status.ACTIVE, null,
                null, null, null, session.getValidTurnCount(), session.getCommittedTurnCount(), List.of(), List.of(),
                false, false, 1L, 1L, true, null, turns);
    }

    private static SpeakingSessionResponse emptyHistory() {
        SpeakingSession session = session(activeSnapshot());
        return historySnapshot(session, List.of());
    }

    private static List<SpeakingSessionResponse.Turn> sixSuccessfulTurns() {
        List<SpeakingSessionResponse.Turn> turns = new ArrayList<>();
        for (int sequence = 1; sequence <= 6; sequence++) {
            turns.add(new SpeakingSessionResponse.Turn("turn-" + sequence, sequence, "learner-" + sequence,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence), true,
                    SpeakingTurn.EvaluationState.SUCCEEDED, "partner-" + sequence, null, false, false));
        }
        turns.add(2, new SpeakingSessionResponse.Turn("invalid", 99, "ignored", Instant.now(), false,
                SpeakingTurn.EvaluationState.INVALID, "ignored", null, false, false));
        return turns;
    }
}
