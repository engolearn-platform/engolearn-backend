package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.ConversationStateView;
import fit.iuh.engolearn.speaking.model.DialogueAct;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.SpeakingAiDraftResponseV1;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConversationManagerTest {
    private final ConversationManager manager = new ConversationManager();
    private static List<JsonNode> fixtures;

    @BeforeAll
    static void loadReviewedFixtures() throws IOException {
        Path path = findWorkspaceFixture("02-conversation-manager-decision-fixtures.json");
        var mapper = new ObjectMapper();
        var loaded = new ArrayList<JsonNode>();
        mapper.readTree(Files.readString(path)).withArray("fixtures").forEach(loaded::add);
        fixtures = List.copyOf(loaded);
    }

    @ParameterizedTest(name = "CM-{0} is present in the reviewed manager fixture set")
    @MethodSource("fixtureIds")
    void loadsEveryReviewedManagerFixtureMetadata(int fixtureNumber) {
        String id = "CM-%02d".formatted(fixtureNumber);
        JsonNode fixture = fixtures.stream().filter(candidate -> id.equals(candidate.path("id").asText()))
                .findFirst().orElseThrow();
        assertFalse(fixture.path("given").isMissingNode());
        assertFalse(fixture.path("expect").isMissingNode());
        assertTrue(fixture.path("traceability").isArray());
    }

    @ParameterizedTest(name = "{0} executes through the pure manager")
    @MethodSource("pureManagerFixtureIds")
    void executesPureManagerFixtureCases(String fixtureId) {
        FixtureExecution execution = executePureFixture(fixtureId);
        JsonNode expect = fixture(fixtureId).path("expect");
        DialogueDecision decision = execution.decision();

        assertEquals(expect.path("outcome").asText(), decision.outcome().name());
        if (expect.has("recoveryKind")) {
            assertEquals(expect.path("recoveryKind").asText(), decision.reasonCode().name());
        }
        if (expect.has("terminationReason") && !expect.path("terminationReason").isNull()) {
            assertEquals(mapTerminationReason(expect.path("terminationReason").asText()), decision.reasonCode());
        }
        if (expect.has("sessionStatus")) {
            assertEquals(expect.path("sessionStatus").asText(), decision.resultingStatus().name());
        }
        if (expect.has("validTurnCountDelta")) {
            assertEquals(expect.path("validTurnCountDelta").asInt(),
                    decision.validTurnCount() - execution.startingValidTurnCount());
        }
        if (expect.has("consecutiveEvaluationErrors")) {
            assertEquals(expect.path("consecutiveEvaluationErrors").asInt(), decision.consecutiveEvaluationErrors());
        }
        if (expect.has("newlyAchievedGoalIds")) {
            List<String> expected = new ArrayList<>();
            expect.path("newlyAchievedGoalIds").forEach(node -> expected.add(node.asText().toLowerCase()));
            assertEquals(expected, decision.newlyAchievedGoalIds());
        }
        if (expect.has("autoEndEligible")) {
            assertEquals(expect.path("autoEndEligible").asBoolean(), decision.autoEndEligible());
        }
        if (expect.has("completionEligible")) {
            assertEquals(expect.path("completionEligible").asBoolean(), decision.completionEligible());
        }
        if (expect.has("acceptedAct")) {
            assertEquals(expect.path("acceptedAct").isNull() ? null : expect.path("acceptedAct").asText(),
                    decision.acceptedAct() == null ? null : decision.acceptedAct().name());
        }
        if (expect.has("spokenReplyToDeliver")) {
            assertEquals(expect.path("spokenReplyToDeliver").isNull() ? null : expect.path("spokenReplyToDeliver").asText(),
                    decision.spokenReplyToDeliver());
        }
    }

    private static Stream<String> pureManagerFixtureIds() {
        return Stream.of("CM-01", "CM-05", "CM-07", "CM-08", "CM-09", "CM-10", "CM-11", "CM-12",
                "CM-13", "CM-14", "CM-15", "CM-17", "CM-18", "CM-19", "CM-20", "CM-21", "CM-27");
    }

    private static FixtureExecution executePureFixture(String fixtureId) {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL), goal("g3", SpeakingGoal.Priority.OPTIONAL));
        ConversationStateView state;
        ValidatedTurnProposal proposal;
        DialogueDecision.SystemTrigger trigger = ConversationManager.SystemTrigger.NONE;
        switch (fixtureId) {
            case "CM-01" -> {
                state = state(snapshot, List.of("g1"), 2, 2, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-05" -> {
                state = state(snapshot, List.of(), 5, 3, 1);
                proposal = ValidatedTurnProposal.invalid();
            }
            case "CM-07" -> {
                state = state(snapshot, List.of(), 0, 0, 1);
                proposal = ValidatedTurnProposal.evaluationFailed();
            }
            case "CM-08" -> {
                state = state(snapshot, List.of(), 0, 0, 2);
                proposal = ValidatedTurnProposal.evaluationFailed();
            }
            case "CM-09" -> {
                state = state(snapshot, List.of(), 0, 0, 2);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-10" -> {
                snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED), goal("g2", SpeakingGoal.Priority.REQUIRED),
                        goal("g3", SpeakingGoal.Priority.OPTIONAL));
                state = state(snapshot, List.of("g1", "g2"), 5, 5, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-11" -> {
                state = state(snapshot, List.of("g1", "g2", "g3"), 3, 3, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-12" -> {
                state = state(snapshot, List.of("g1"), 4, 4, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("g2", "g3"),
                        dialogue(DialogueAct.REACT_AND_ASK, "And would you like anything else?", SpeakingAiDraftResponseV1.ContinuationType.QUESTION, null));
            }
            case "CM-13" -> {
                state = state(snapshot, List.of(), 0, 0, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.CLOSING_UTTERANCE,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Sure, take your time.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-14" -> {
                state = state(snapshot, List.of("g1"), 5, 5, 0);
                proposal = null;
                trigger = ConversationManager.SystemTrigger.USER_END;
            }
            case "CM-15" -> {
                state = state(snapshot, List.of(), 3, 3, 0);
                proposal = null;
                trigger = ConversationManager.SystemTrigger.USER_END;
            }
            case "CM-17" -> {
                state = state(snapshot, List.of("g1"), 0, 0, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("g2"),
                        dialogue(DialogueAct.GUIDE_GOAL, "Would you like to ask about the price?", SpeakingAiDraftResponseV1.ContinuationType.QUESTION, "g2"));
            }
            case "CM-18" -> {
                state = state(snapshot, List.of(), 0, 0, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.WRAP_UP, "Thank you for visiting.", SpeakingAiDraftResponseV1.ContinuationType.NONE, null));
            }
            case "CM-19" -> {
                state = state(snapshot, List.of(), 0, 0, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("g1"),
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));
            }
            case "CM-20" -> {
                state = state(snapshot, List.of(), 0, 0, 0);
                proposal = ValidatedTurnProposal.evaluationFailed();
            }
            case "CM-21" -> {
                state = state(snapshot, List.of(), 0, 0, 0);
                proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("g1"), null);
            }
            case "CM-27" -> {
                state = state(snapshot, List.of(), 0, 0, 2);
                proposal = ValidatedTurnProposal.invalid();
            }
            default -> throw new IllegalArgumentException("Unsupported pure fixture: " + fixtureId);
        }
        return new FixtureExecution(new ConversationManager().decide(proposal, state, snapshot, trigger), state.validTurnCount());
    }

    private static JsonNode fixture(String fixtureId) {
        return fixtures.stream().filter(candidate -> fixtureId.equals(candidate.path("id").asText()))
                .findFirst().orElseThrow();
    }

    private static DialogueDecision.ReasonCode mapTerminationReason(String reason) {
        return switch (reason) {
            case "USER_END" -> DialogueDecision.ReasonCode.USER_END_ACTION;
            case "AUTO_ALL_GOALS_AND_MIN_TURNS" -> DialogueDecision.ReasonCode.AUTO_ALL_GOALS_AND_MIN_TURNS;
            case "EVALUATION_ERRORS_3" -> DialogueDecision.ReasonCode.EVALUATION_ERRORS_3;
            default -> throw new IllegalArgumentException("Unsupported fixture termination reason: " + reason);
        };
    }

    private record FixtureExecution(DialogueDecision decision, int startingValidTurnCount) {}

    private static Stream<Integer> fixtureIds() {
        return IntStream.rangeClosed(1, 30).boxed();
    }

    private static Path findWorkspaceFixture(String fileName) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(Path.of("Note", "speaking-with-AI", "redesign-inside", fileName));
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace fixture: " + fileName);
    }

    @Test
    void thirdEvaluationFailureIsTerminalWithoutValidTurnOrReply() {
        SpeakingContentSnapshot snapshot = v2(
                goal("g1", SpeakingGoal.Priority.REQUIRED));
        ConversationStateView state = state(snapshot, List.of(), 2, 2, 2);

        DialogueDecision decision = manager.decide(ValidatedTurnProposal.evaluationFailed(), state, snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.EVALUATION_ERRORS_3, decision.reasonCode());
        assertEquals(3, decision.consecutiveEvaluationErrors());
        assertEquals(2, decision.validTurnCount());
        assertNull(decision.acceptedAct());
        assertNull(decision.spokenReplyToDeliver());
    }

    @Test
    void successfulEvaluationResetsConsecutiveErrors() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        DialogueDecision decision = manager.decide(valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null)),
                state(snapshot, List.of(), 2, 2, 2), snapshot, ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertEquals(0, decision.consecutiveEvaluationErrors());
    }

    @Test
    void invalidFinalizedTurnUsesRecoveryWithoutEvaluationError() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ConversationStateView state = state(snapshot, List.of(), 5, 3, 2);

        DialogueDecision decision = manager.decide(ValidatedTurnProposal.invalid(), state, snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.RECOVERY, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.INVALID_TURN, decision.reasonCode());
        assertEquals(5, decision.finalizedTurnCount());
        assertEquals(3, decision.validTurnCount());
        assertEquals(2, decision.consecutiveEvaluationErrors());
        assertNull(decision.acceptedAct());
    }

    @Test
    void directQuestionCannotBeReplacedByGoalGuidance() {
        SpeakingContentSnapshot snapshot = v2(
                goal("g1", SpeakingGoal.Priority.REQUIRED));
        ConversationStateView state = state(snapshot, List.of(), 1, 1, 0);
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.QUESTION,
                dialogue(DialogueAct.GUIDE_GOAL, "Would you like a drink?", SpeakingAiDraftResponseV1.ContinuationType.QUESTION, "g1"));

        DialogueDecision decision = manager.decide(proposal, state, snapshot, ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.CLARIFICATION_FALLBACK, decision.reasonCode());
        assertEquals(DialogueAct.CLARIFY, decision.acceptedAct());
        assertEquals("Could you clarify what you mean?", decision.spokenReplyToDeliver());
    }

    @Test
    void answerAndAskDoesNotRequireQuestionMark() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.QUESTION,
                dialogue(DialogueAct.ANSWER_AND_ASK, "You can tap your card here.", SpeakingAiDraftResponseV1.ContinuationType.ACTION_PROMPT, null));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueAct.ANSWER_AND_ASK, decision.acceptedAct());
        assertEquals("You can tap your card here.", decision.spokenReplyToDeliver());
    }

    @Test
    void wrapUpIsNotAllowedInNormalPath() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                dialogue(DialogueAct.WRAP_UP, "Thank you for visiting.", SpeakingAiDraftResponseV1.ContinuationType.NONE, null));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.WRAP_UP_NOT_AUTHORIZED, decision.reasonCode());
        assertNull(decision.acceptedAct());
        assertNull(decision.spokenReplyToDeliver());
        assertNull(decision.systemTrigger());
    }

    @Test
    void guideGoalAlreadyAchievedByCurrentProposalIsSuppressed() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                List.of("g2"), dialogue(DialogueAct.GUIDE_GOAL, "Would you like to ask about the price?",
                        SpeakingAiDraftResponseV1.ContinuationType.QUESTION, "g2"));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.ReasonCode.TARGET_ALREADY_ACHIEVED, decision.reasonCode());
        assertNull(decision.acceptedAct());
        assertEquals(List.of("g2"), decision.newlyAchievedGoalIds());
        assertEquals(2, decision.validTurnCount());
    }

    @Test
    void allGoalsAndMinimumTurnsBecomeTerminalAndSuppressNormalReply() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                List.of("g2"), dialogue(DialogueAct.REACT_AND_ASK, "Would you like anything else?",
                        SpeakingAiDraftResponseV1.ContinuationType.QUESTION, null));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of("g1"), 2, 4, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.AUTO_ALL_GOALS_AND_MIN_TURNS, decision.reasonCode());
        assertEquals(SpeakingSession.Status.COMPLETED, decision.resultingStatus());
        assertNull(decision.spokenReplyToDeliver());
    }

    @Test
    void allGoalsBeforeMinimumTurnsStayActive() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        ConversationStateView state = state(snapshot, List.of("g1", "g2"), 2, 3, 0);
        DialogueDecision decision = manager.decide(valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null)),
                state, snapshot, ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertFalse(decision.autoEndEligible());
        assertNotEquals(DialogueAct.WRAP_UP, decision.acceptedAct());
        assertEquals(SpeakingSession.Status.ACTIVE, decision.resultingStatus());
    }

    @Test
    void spokenStopIsNotUserEnd() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.CLOSING_UTTERANCE,
                dialogue(DialogueAct.REACT_AND_INVITE, "Sure, take your time.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.SPOKEN_STOP_NOT_END, decision.reasonCode());
        assertNull(decision.systemTrigger());
        assertEquals(SpeakingSession.Status.ACTIVE, decision.resultingStatus());
        assertEquals(DialogueAct.REACT_AND_INVITE, decision.acceptedAct());
        assertEquals("Thanks for practicing with me. Goodbye.", decision.spokenReplyToDeliver());
    }

    @Test
    void missingPartnerReplyKeepsAssessmentAndLeavesExplicitMissingReplyNoticeToRuntime() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, null);

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.DIALOGUE_NOT_AVAILABLE, decision.reasonCode());
        assertNull(decision.acceptedAct());
        assertNull(decision.spokenReplyToDeliver());
    }

    @Test
    void explicitEndCompletesWhenRequiredAndMinimumAreMet() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        DialogueDecision decision = manager.decide(null, state(snapshot, List.of("g1"), 3, 5, 0), snapshot,
                ConversationManager.SystemTrigger.USER_END);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.USER_END_ACTION, decision.reasonCode());
        assertEquals(ConversationManager.SystemTrigger.USER_END, decision.systemTrigger());
        assertEquals(SpeakingSession.Status.COMPLETED, decision.resultingStatus());
    }

    @Test
    void timelyPendingAssessmentAfterEndKeepsUserEndReasonAndCanComplete() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ConversationStateView closingState = new ConversationStateView(SpeakingSession.Status.ACTIVE, 3, 4, 0,
                Set.of(), Instant.parse("2026-11-01T00:00:00Z"), SpeakingSession.EndReason.LEARNER_ENDED,
                1L, 1L);
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("g1"),
                dialogue(DialogueAct.REACT_AND_ASK, "Would you like anything else?",
                        SpeakingAiDraftResponseV1.ContinuationType.QUESTION, null));

        DialogueDecision decision = manager.decide(proposal, closingState, snapshot,
                ConversationManager.SystemTrigger.USER_END);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.USER_END_ACTION, decision.reasonCode());
        assertEquals(SpeakingSession.Status.COMPLETED, decision.resultingStatus());
        assertEquals(List.of("g1"), decision.newlyAchievedGoalIds());
        assertNull(decision.spokenReplyToDeliver());
    }

    @Test
    void closingWithoutPendingProposalUsesPinnedClosingReason() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ConversationStateView closingState = new ConversationStateView(SpeakingSession.Status.ACTIVE, 3, 4, 0,
                Set.of(), Instant.parse("2026-11-01T00:00:00Z"), SpeakingSession.EndReason.LEARNER_ENDED,
                1L, 1L);

        DialogueDecision decision = manager.decide(null, closingState, snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(DialogueDecision.ReasonCode.USER_END_ACTION, decision.reasonCode());
        assertEquals(SpeakingSession.Status.INCOMPLETE, decision.resultingStatus());
    }

    @Test
    void thirdEvaluationFailurePreservesAlreadyEligibleCompletion() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        ConversationStateView state = state(snapshot, List.of("g1"), 5, 5, 2);

        DialogueDecision decision = manager.decide(ValidatedTurnProposal.evaluationFailed(), state, snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.TERMINAL, decision.outcome());
        assertEquals(SpeakingSession.Status.COMPLETED, decision.resultingStatus());
    }

    @Test
    void acceptedGuideGoalRetainsItsTarget() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED));
        ValidatedTurnProposal proposal = valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                dialogue(DialogueAct.GUIDE_GOAL, "Would you like to ask about the price?",
                        SpeakingAiDraftResponseV1.ContinuationType.QUESTION, "g1"));

        DialogueDecision decision = manager.decide(proposal, state(snapshot, List.of(), 1, 1, 0), snapshot,
                ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueAct.GUIDE_GOAL, decision.acceptedAct());
        assertEquals("g1", decision.targetGoalId());
    }

    @Test
    void requiredMilestoneAloneStaysActive() {
        SpeakingContentSnapshot snapshot = v2(goal("g1", SpeakingGoal.Priority.REQUIRED),
                goal("g2", SpeakingGoal.Priority.OPTIONAL));
        DialogueDecision decision = manager.decide(valid(SpeakingAiDraftResponseV1.UserIntent.STATEMENT,
                        dialogue(DialogueAct.REACT_AND_INVITE, "Tell me more.", SpeakingAiDraftResponseV1.ContinuationType.INVITATION, null)),
                state(snapshot, List.of("g1"), 2, 5, 0), snapshot, ConversationManager.SystemTrigger.NONE);

        assertEquals(DialogueDecision.Outcome.NORMAL, decision.outcome());
        assertTrue(decision.requiredMilestoneMet());
        assertTrue(decision.completionEligible());
        assertFalse(decision.autoEndEligible());
        assertEquals(SpeakingSession.Status.ACTIVE, decision.resultingStatus());
    }

    private static ValidatedTurnProposal valid(SpeakingAiDraftResponseV1.UserIntent intent,
            SpeakingAiDraftResponseV1.Dialogue dialogue) {
        return valid(intent, List.of(), dialogue);
    }

    private static ValidatedTurnProposal valid(SpeakingAiDraftResponseV1.UserIntent intent, List<String> goalIds,
            SpeakingAiDraftResponseV1.Dialogue dialogue) {
        return ValidatedTurnProposal.valid(intent, goalIds, dialogue);
    }

    private static SpeakingAiDraftResponseV1.Dialogue dialogue(DialogueAct act, String reply,
            SpeakingAiDraftResponseV1.ContinuationType continuation, String targetGoalId) {
        return new SpeakingAiDraftResponseV1.Dialogue(act, reply, continuation, null, targetGoalId);
    }

    private static SpeakingContentSnapshot v2(SpeakingGoal... goals) {
        return new SpeakingContentSnapshot("item", "version", "Title", "Description", List.of(goals), null,
                5, List.of(), List.of(), 1L, "REQUIRED_GOALS_V2");
    }

    private static SpeakingGoal goal(String id, SpeakingGoal.Priority priority) {
        return new SpeakingGoal(id, id, id, priority);
    }

    private static ConversationStateView state(SpeakingContentSnapshot snapshot, List<String> achieved,
            int finalized, int valid, int errors) {
        return new ConversationStateView(SpeakingSession.Status.ACTIVE, finalized, valid, errors,
                Set.copyOf(achieved), null, null, 1L, 1L);
    }
}
