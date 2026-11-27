package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.quota.contract.QuotaUsageRecording;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingTurnStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingChargeVo;
import fit.iuh.engolearn.speaking.repository.SpeakingSessionRepository;
import fit.iuh.engolearn.speaking.repository.SpeakingTurnArchive;
import fit.iuh.engolearn.speaking.repository.SpeakingTurnRepository;
import fit.iuh.engolearn.speaking.service.SpeakingQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakingQuotaImpl implements SpeakingQuota {
    private final QuotaUsageRecording quota;
    private final SpeakingSessionRepository sessions;
    private final SpeakingTurnRepository turns;
    private final SpeakingTurnArchive archive;
    private final Clock clock;

    @Override
    public void pending(String sessionId) {
        var session = sessions.findById(sessionId);
        if (session.isEmpty()) return;
        for (var turn : turns.findBySessionIdOrderByValueSequenceAsc(sessionId)) {
            if (!turn.isQuotaRecorded()) record(session.get(), turn);
        }
    }

    @Override
    public boolean pendingOwner(String ownerId) {
        for (var row : archive.pendingChargesForOwner(ownerId, clock.instant(), 100)) {
            try {
                sessions.findById(row.getSessionId()).ifPresent(session -> record(session, row));
            } catch (RuntimeException unavailable) {
                log.warn("Speaking owner settlement remains pending for turn {}", row.getId());
                return false;
            }
        }
        return !archive.hasPendingChargesForOwner(ownerId);
    }

    @Override
    public java.util.Set<String> recover() {
        var owners = new java.util.LinkedHashSet<String>();
        for (var row : archive.pendingCharges(clock.instant(), 100)) {
            try {
                var session = sessions.findById(row.getSessionId()).orElse(null);
                if (session != null) record(session, row);
                var settled = turns.findById(row.getId()).orElse(null);
                if (session != null && settled != null && settled.isQuotaRecorded() && settled.getCharge() != null
                        && settled.getCharge().seconds() > 0) owners.add(session.getOwnerId());
            } catch (RuntimeException failure) {
                archive.deferCharge(row, clock.instant());
                log.warn("Speaking charge is still pending for turn {}", row.getId());
            }
        }
        return owners;
    }

    private void record(SpeakingSession session, SpeakingTurn row) {
        var turn = row.getValue();
        if (!turn.getStatus().terminal() || turn.getSealedAt() == null) return;
        // A retry contributes to the same charge. Wait until the prior attempt can no longer be retried.
        if (retryOpen(session, row)) {
            archive.deferCharge(row, clock.instant());
            return;
        }
        if (turn.getAudioStatus() == SpeakingAudioStatus.AUDIO_PENDING) {
            if (turn.getDeliveryDeadline().isAfter(clock.instant())) return;
            var ref = turn.getAssistantAudio();
            archive.finishAudio(row.getSessionId(), row.getId(), turn.getDeliveryToken(),
                    ref == null ? SpeakingAudioStatus.AUDIO_FAILED : SpeakingAudioStatus.AUDIO_READY,
                    ref, 0, clock.instant(), true);
            row = turns.findById(row.getId()).orElseThrow();
            turn = row.getValue();
            if (turn.getAudioStatus() == SpeakingAudioStatus.AUDIO_PENDING) return;
        }
        SpeakingChargeVo charge = row.getCharge();
        if (charge == null) {
            charge = archive.prepareCharge(row, new SpeakingChargeVo(session.getOwnerId(),
                    Optional.ofNullable(turn.getRecordingStartedAt()).orElse(turn.getSealedAt()), turn.getSpeechDurationMs(),
                    turn.getSuccessfulWaitMs(), turn.getDeliveredAudioMs()), clock.instant());
            if (charge == null) return;
        }
        String ledgerId = null;
        Instant recordedAt = null;
        if (charge.seconds() > 0) {
            var recorded = quota.recordUsage(new UsageCommandDto(charge.ownerId(), "SPEAKING_DURATION", charge.seconds(),
                    "SPEAKING", "speaking-v2-" + row.getId(), row.getId(), charge.occurredAt()));
            if (recorded == null || !recorded.accepted() || recorded.ledgerId() == null || recorded.ledgerId().isBlank())
                throw new IllegalStateException("Speaking quota did not confirm a durable ledger receipt");
            ledgerId = recorded.ledgerId();
            recordedAt = recorded.recordedAt();
        }
        archive.confirmCharge(row.getSessionId(), row.getId(), charge.ownerId(), ledgerId, recordedAt, clock.instant());
    }

    private static boolean retryOpen(SpeakingSession session, SpeakingTurn row) {
        var turn = row.getValue();
        return session.getStatus() == SpeakingSessionStatus.ACTIVE && turn.getSequence() == session.getLastTurnSequence()
                && turn.getSequence() > session.getRetryClosedThroughSequence()
                && turn.getStatus() == SpeakingTurnStatus.FAILED && turn.getAttempt() == 0
                && "CONVERSATION_UNAVAILABLE".equals(turn.getReason()) && turn.getTranscript() != null;
    }
}
