package fit.iuh.engolearn.speaking.mapper;

import fit.iuh.engolearn.speaking.dto.response.SpeakingAssessmentResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.vo.*;
import fit.iuh.engolearn.speaking.utils.SpeakingSessionSettings;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingAccessDto.Snapshot;
import java.time.Instant;

public final class SpeakingSessionMapper {
    private SpeakingSessionMapper() {}

    public static SpeakingSnapshotVo snapshot(Snapshot snapshot) {
        var scope = snapshot.scope();
        var profile = snapshot.profile();
        var hint = profile.fallbackHint();
        return SpeakingSnapshotVo.builder().scopeType(scope.type()).topicId(scope.topicId()).topicItemId(scope.topicItemId())
                .title(snapshot.title()).topicTitle(snapshot.topicTitle()).topicItemTitle(snapshot.topicItemTitle())
                .contentVersion(snapshot.contentVersion()).profileVersion(snapshot.profileVersion())
                .level(profile.level()).description(profile.description()).aiRole(profile.aiRole()).learnerRole(profile.learnerRole())
                .suggestedTopics(profile.suggestedTopics()).resources(snapshot.resources().stream()
                        .map(resource -> new SpeakingResourceVo(resource.id(), resource.type(), resource.textEn(),
                                resource.context())).toList())
                .fallbackReply(profile.fallbackReply())
                .fallbackHint(hint == null ? null : new SpeakingHintVo(hint.exampleEn(), hint.explanationVi())).build();
    }

    public static SpeakingScopeDto scope(SpeakingSnapshotVo snapshot) {
        return new SpeakingScopeDto(snapshot.getScopeType(), snapshot.getTopicId(), snapshot.getTopicItemId());
    }

    public static Current current(SpeakingSession session) {
        return session == null ? null : new Current(session.getId(), scope(session.getSnapshot()),
                session.getStatus(), session.getControllerEpoch());
    }

    public static Session session(SpeakingSession session, Instant now) {
        var snapshot = session.getSnapshot();
        var context = new Context(snapshot.getTitle(), snapshot.getTopicTitle(), snapshot.getTopicItemTitle(),
                snapshot.getLevel(), snapshot.getDescription(), snapshot.getAiRole(), snapshot.getLearnerRole());
        var limits = session.getLimits();
        var opening = session.getOpening();
        var active = session.getActiveTurn();
        return new Session(session.getId(), scope(snapshot), context, session.getStatus(), session.getEndReason(),
                session.getControllerEpoch(), "/ws/speaking/" + session.getId(),
                new Limits(limits.getMaxTurns(), limits.getMaxSessionMs(), limits.getMaxTurnAudioMs()),
                session.getCreatedAt(), session.getStartedAt(), session.getEndedAt(),
                opening == null ? null : new Opening(opening.getReply(), hint(opening.getHint()), opening.getAudioStatus(),
                        audioAvailable(opening.getAudio(), now)),
                session.getFinalizedTurnCount(), session.getCommittedTurnCount(), session.getSpeechDurationMs(),
                session.getAssessmentStatus(), active == null || active.getStatus().terminal() ? null
                        : new ActiveTurn(active.getTurnId(), active.getSequence(), active.getClientTurnId(), active.getStatus()));
    }

    public static Turn turn(SpeakingTurnVo turn, Instant now) {
        var correction = turn.getCorrection();
        return new Turn(turn.getTurnId(), turn.getClientTurnId(), turn.getSequence(), turn.getStatus(), turn.getTranscript(),
                turn.getReply(), hint(turn.getHint()), correction == null ? null : new Correction(correction.getType(),
                correction.getOriginal(), correction.getSuggested(), correction.getExplanationVi()),
                audioAvailable(turn.getLearnerAudio(), now), audioAvailable(turn.getAssistantAudio(), now), turn.getAudioStatus(),
                turn.getReplyDeliveredAt(), turn.getSpeechDurationMs(), turn.getReason());
    }

    public static SpeakingAssessmentResponse assessment(SpeakingSession session) {
        var data = session.getAssessment();
        if (data == null) return SpeakingAssessmentResponse.pending(session.getId(), session.getAssessmentStatus());
        var criteria = new SpeakingAssessmentResponse.Criteria(criterion(data.getPronunciation(), 100),
                criterion(data.getFluency(), 100), criterion(data.getGrammar(), 5), criterion(data.getVocabulary(), 5));
        var feedback = data.getFeedback();
        var responseFeedback = new SpeakingAssessmentResponse.Feedback(feedback.getStrengthsVi(), feedback.getImprovementsVi(),
                feedback.getRecommendationsVi(), feedback.getWordsToPractice().stream().map(word ->
                new SpeakingAssessmentResponse.Word(word.getWord(), word.getAccuracyScore(), word.getErrorType())).toList());
        return new SpeakingAssessmentResponse(session.getId(), session.getAssessmentStatus(), criteria, responseFeedback,
                data.getFinalizedAt());
    }

    private static SpeakingAssessmentResponse.Criterion criterion(SpeakingCriterionVo value, int maximum) {
        return new SpeakingAssessmentResponse.Criterion(value.getStatus().name(), value.getScore(), maximum, value.getReason(),
                value.getSampleCount(), value.getWordCount(), value.getSpeechDurationMs(), component(value.getAccuracy()),
                component(value.getRange()), value.getExamples().stream().map(example ->
                new SpeakingAssessmentResponse.Example(example.getTurnId(), example.getOriginal(), example.getSuggested())).toList());
    }

    private static SpeakingAssessmentResponse.Component component(SpeakingRubricComponentVo value) {
        if (value == null) return null;
        return new SpeakingAssessmentResponse.Component(value.getStatus().name(),
                value.getLevel() == null ? null : value.getLevel().toString(), value.getReasonVi());
    }

    public static boolean audioAvailable(SpeakingMediaVo audio, Instant now) {
        return audio != null && audio.getMediaId() != null && audio.getStoredAt() != null
                && audio.getStoredAt().plus(SpeakingSessionSettings.AUDIO_RETENTION).isAfter(now);
    }

    private static Hint hint(SpeakingHintVo hint) {
        return hint == null ? null : new Hint(hint.getExampleEn(), hint.getExplanationVi());
    }
}
