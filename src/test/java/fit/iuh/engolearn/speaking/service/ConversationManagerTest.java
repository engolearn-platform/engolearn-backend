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
    void loadsEveryReviewedManagerFixture(int fixtureNumber) {
        String id = "CM-%02d".formatted(fixtureNumber);
        JsonNode fixture = fixtures.stream().filter(candidate -> id.equals(candidate.path("id").asText()))
                .findFirst().orElseThrow();
        assertFalse(fixture.path("given").isMissingNode());
        assertFalse(fixture.path("expect").isMissingNode());
        assertTrue(fixture.path("traceability").isArray());
    }

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
        assertEquals(DialogueDecision.ReasonCode.ANSWER_FIRST_REQUIRED, decision.reasonCode());
        assertNull(decision.acceptedAct());
        assertNull(decision.spokenReplyToDeliver());
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
