package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.model.ConversationStateView;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.GatewayOutcome;
import fit.iuh.engolearn.speaking.model.SpeakingAiRequestV1;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingConversationAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;
import fit.iuh.engolearn.speaking.utils.PromptV2Builder;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SpeakingConversationComposer {
    private static final int MAX_HISTORY_EXCHANGES = 5;

    private final SpeakingAiGateway gateway;
    private final SpeakingDraftValidator validator;
    private final ConversationManager manager;

    public SpeakingConversationComposer(SpeakingAiGateway gateway, SpeakingDraftValidator validator,
            ConversationManager manager) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public SpeakingConversationAssessment assess(SpeakingSession session, SpeakingSessionResponse historySnapshot,
            String finalizedTranscript) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(historySnapshot, "historySnapshot");
        Objects.requireNonNull(finalizedTranscript, "finalizedTranscript");

        SpeakingContentSnapshot snapshot = Objects.requireNonNull(session.getContent(), "session.content");
        List<String> achievedGoalIds = session.getAchievedGoalIds() == null
                ? List.of() : List.copyOf(session.getAchievedGoalIds());
        Map<String, SpeakingDraftValidator.PinnedResource> validationResources = validationResources(snapshot);
        SpeakingAiRequestV1 request = new SpeakingAiRequestV1(
                PromptV2Builder.DRAFT_CONTRACT,
                PromptV2Builder.PROMPT_VERSION,
                pinnedSnapshot(snapshot),
                achievedGoalIds,
                recentHistory(historySnapshot),
                requestResources(snapshot),
                finalizedTranscript);

        GatewayOutcome gatewayOutcome = gateway.assess(request);
        ValidatedTurnProposal proposal = proposal(gatewayOutcome, finalizedTranscript, snapshot,
                validationResources, achievedGoalIds);
        DialogueDecision decision = manager.decide(proposal, ConversationStateView.from(session), snapshot,
                ConversationManager.SystemTrigger.NONE);
        return new SpeakingConversationAssessment(proposal, decision);
    }

    private ValidatedTurnProposal proposal(GatewayOutcome outcome, String transcript,
            SpeakingContentSnapshot snapshot, Map<String, SpeakingDraftValidator.PinnedResource> resources,
            List<String> achievedGoalIds) {
        if (!(outcome instanceof GatewayOutcome.Success success)) return ValidatedTurnProposal.evaluationFailed();
        try {
            String candidate = success.draft().candidateJson();
            var context = new SpeakingDraftValidator.ValidationContext(transcript, snapshot, resources, achievedGoalIds);
            return candidate.contains("speaking.ai.draft.v2")
                    ? validator.validateV2(candidate, context)
                    : validator.validate(candidate, context);
        } catch (SpeakingDraftValidationException exception) {
            return ValidatedTurnProposal.evaluationFailed();
        }
    }

    private SpeakingAiRequestV1.PinnedSnapshot pinnedSnapshot(SpeakingContentSnapshot snapshot) {
        return new SpeakingAiRequestV1.PinnedSnapshot(snapshot.topicItemId(), snapshot.topicItemVersionId(),
                snapshot.title(), snapshot.description(), snapshot.goals().stream()
                .map(goal -> new SpeakingAiRequestV1.PinnedGoal(goal.id(), goal.title(), goal.description(), goal.priority()))
                .toList(), snapshot.vocabulary(), snapshot.expressions(),
                snapshot.context(), snapshot.learnerRole(), snapshot.aiRole());
    }

    private List<SpeakingAiRequestV1.PinnedResource> requestResources(SpeakingContentSnapshot snapshot) {
        List<SpeakingAiRequestV1.PinnedResource> resources = new ArrayList<>();
        for (int index = 0; index < snapshot.vocabulary().size(); index++) {
            resources.add(new SpeakingAiRequestV1.PinnedResource("vocabulary:" + index, "VOCABULARY",
                    snapshot.vocabulary().get(index)));
        }
        for (int index = 0; index < snapshot.expressions().size(); index++) {
            resources.add(new SpeakingAiRequestV1.PinnedResource("expression:" + index, "EXPRESSION",
                    snapshot.expressions().get(index)));
        }
        return List.copyOf(resources);
    }

    private Map<String, SpeakingDraftValidator.PinnedResource> validationResources(SpeakingContentSnapshot snapshot) {
        Map<String, SpeakingDraftValidator.PinnedResource> resources = new LinkedHashMap<>();
        for (SpeakingAiRequestV1.PinnedResource resource : requestResources(snapshot)) {
            resources.put(resource.id(), new SpeakingDraftValidator.PinnedResource(resource.id(), resource.kind(),
                    resource.text()));
        }
        return Map.copyOf(resources);
    }

    private List<SpeakingAiRequestV1.HistoryTurn> recentHistory(SpeakingSessionResponse historySnapshot) {
        List<SpeakingSessionResponse.Turn> successfulTurns = historySnapshot.turns().stream()
                .filter(Objects::nonNull)
                .filter(turn -> turn.valid() && turn.evaluationState() == SpeakingTurn.EvaluationState.SUCCEEDED)
                .filter(turn -> nonBlank(turn.transcript()) && nonBlank(turn.aiReply()))
                .toList();
        int first = Math.max(0, successfulTurns.size() - MAX_HISTORY_EXCHANGES);
        List<SpeakingAiRequestV1.HistoryTurn> history = new ArrayList<>();
        if (historySnapshot.opening() != null && nonBlank(historySnapshot.opening().reply())) {
            history.add(new SpeakingAiRequestV1.HistoryTurn(
                    SpeakingAiRequestV1.Role.PARTNER, historySnapshot.opening().reply()));
        }
        for (SpeakingSessionResponse.Turn turn : successfulTurns.subList(first, successfulTurns.size())) {
            history.add(new SpeakingAiRequestV1.HistoryTurn(SpeakingAiRequestV1.Role.LEARNER, turn.transcript()));
            history.add(new SpeakingAiRequestV1.HistoryTurn(SpeakingAiRequestV1.Role.PARTNER, turn.aiReply()));
        }
        return List.copyOf(history);
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
