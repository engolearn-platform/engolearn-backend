package fit.iuh.engolearn.speaking.mapper;

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
                        .map(resource -> new SpeakingResourceVo(resource.id(), resource.type(), resource.textEn())).toList())
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

    public static boolean audioAvailable(SpeakingMediaVo audio, Instant now) {
        return audio != null && audio.getMediaId() != null && audio.getStoredAt() != null
                && audio.getStoredAt().plus(SpeakingSessionSettings.AUDIO_RETENTION).isAfter(now);
    }

    private static Hint hint(SpeakingHintVo hint) {
        return hint == null ? null : new Hint(hint.getExampleEn(), hint.getExplanationVi());
    }
}
