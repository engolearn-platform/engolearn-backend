package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.utils.*;

import fit.iuh.engolearn.assessment.service.*;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import fit.iuh.engolearn.assessment.model.vo.AssessmentPaperSnapshotVo;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelPromotion;
import fit.iuh.engolearn.user.contract.UserAssessmentProgress;
import fit.iuh.engolearn.user.contract.dto.UserAssessmentProgressDto;
import fit.iuh.engolearn.user.contract.exception.UserLevelPromotionException;
import fit.iuh.engolearn.assessment.model.enums.*;

import fit.iuh.engolearn.assessment.contract.AssessmentBaselineSource;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentBaselineDto;
import fit.iuh.engolearn.assessment.dto.request.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.mapper.AssessmentMapper;
import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import static fit.iuh.engolearn.assessment.model.AssessmentTimeline.*;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {
    private static final String QUOTA="PROFICIENCY_CHECK_ATTEMPT";
    private final AssessmentRepository store;
    private final AssessmentTransactions transactions;
    private final AssessmentBaselineSource baselines;
    private final AssessmentPaperSelection selection;
    private final QuotaAvailability availability;
    private final QuotaConsumption consumption;
    private final AssessmentAccess access;
    private final AssessmentMedia media;
    private final UserLevelPromotion userLevels;
    private final UserAssessmentProgress userAssessmentProgress;
    private final Clock clock;
    private final Environment environment;
    private boolean development() {var p=List.of(environment.getActiveProfiles());return p.contains("dev")&&!p.contains("prod");}

    public AssessmentPreparationResponse preparation(String user) {
        return transactions.execute(user,()->{
            Instant now=now();
            var active=store.active(user);
            if(active!=null) {settleAndGrade(active,now);store.save(active);if(!AssessmentStatus.ACTIVE.equals(active.getStatus())) active=null;}
            var p=profile(user);
            var baseline=baselines.findValidBaseline(user);
            boolean valid=p!=null&&p.latestValidAt()!=null||baseline.isPresent();
            Instant eligible=p==null||p.latestValidAt()==null?null:p.latestValidAt().plus(Duration.ofDays(21));
            return new AssessmentPreparationResponse(valid,eligible,eligible==null||!now.isBefore(eligible),
                    active==null?availability.checkAvailability(user,QUOTA):null,active==null?null:active.getId(),
                    !papers().isEmpty(),development(),p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.initialLevel(),
                    p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.currentLevel(),
                    p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.highestLevel());
        });
    }
    public AssessmentStateResponse start(String user, AssessmentStartRequest command) {
        tickUser(user);
        var plan = transactions.execute(user, () -> {
            Instant now = now();
            var previous = replayStart(user, command, now);
            if (previous != null) return new StartPlan(previous, null);
            checkStartEligibility(user, now);
            var history = store.valid(user).stream()
                    .filter(attempt -> development() || !attempt.getPaper().isDemonstration()).toList();
            return new StartPlan(null, selection.choose(papers(), history, now));
        });
        if (plan.replay() != null) return plan.replay();

        var paper = plan.paper();
        AssessmentPaperSnapshotVo snapshot;
        try {
            // File reads and Media resolution stay outside the short transaction that owns usage.
            selection.validate(paper);
            snapshot = AssessmentMapper.snapshot(paper);
            for (var unit : snapshot.getUnits()) unit.setAudioMediaId(media.durableReference(unit));
        } catch (AssessmentException ex) {
            return transactions.execute(user, () -> {
                var previous = replayStart(user, command, now());
                if (previous != null) return previous;
                throw ex;
            });
        }

        String publicationGate = "paper:" + paper.getSeries();
        store.ensureGate(publicationGate);
        return transactions.execute(user, () -> {
            var previous = replayStart(user, command, now());
            if (previous != null) return previous;
            // Publication and archive hold the same series gate before changing eligibility.
            store.lock(publicationGate);
            if (!store.isPublished(paper.getId())) {
                fail(409, "PAPER_CHANGED", "Bộ đề vừa thay đổi. Hãy bắt đầu lại.");
            }
            Instant at = now();
            var eligibility = checkStartEligibility(user, at);
            var attempt = new AssessmentAttempt();
            attempt.setId(UUID.randomUUID().toString());
            attempt.setUserId(user);
            attempt.setRequestId(command.requestId());
            attempt.setBindingHash(hash(command.binding()));
            attempt.setStartedAt(at);
            attempt.setPaper(snapshot);
            open(attempt, 0, at);
            access.currentGrant(user).ifPresent(attempt.getAnalysisGrants()::add);

            var consumed = consumption.consume(new UsageCommandDto(user, QUOTA, 1, "assessment",
                    "pfc:" + command.requestId(), attempt.getId(), at));
            if (!consumed.accepted()) fail(409, "QUOTA_EXHAUSTED", "Đã hết lượt đánh giá trong tháng.");
            if (eligibility.profile() == null) {
                String level = eligibility.baseline().orElseThrow().level();
                userAssessmentProgress.save(user, new UserAssessmentProgressDto(
                        level, level, level, null, null, development()));
            }
            store.save(attempt);
            return AssessmentMapper.state(attempt, at, development());
        });
    }

    private AssessmentStateResponse replayStart(String user, AssessmentStartRequest command, Instant at) {
        var previous = store.request(user, command.requestId());
        if (previous == null) return null;
        bind(previous, command.binding());
        settleAndGrade(previous, at);
        store.save(previous);
        return AssessmentMapper.state(previous, at, development());
    }

    private StartEligibility checkStartEligibility(String user, Instant at) {
        if (store.active(user) != null) {
            fail(409, "ACTIVE_ATTEMPT", "Bạn đang có bài ở trang khác. Hãy quay lại hoặc xác nhận bỏ bài cũ.");
        }
        var profile = profile(user);
        var baseline = baselines.findValidBaseline(user);
        if ((profile == null || profile.latestValidAt() == null) && baseline.isEmpty()) {
            fail(409, "BASELINE_REQUIRED", "Cần có kết quả đầu vào hợp lệ trước khi bắt đầu.");
        }
        if (profile != null && profile.latestValidAt() != null
                && at.isBefore(profile.latestValidAt().plus(Duration.ofDays(21)))) {
            fail(409, "COOLDOWN", "Chưa đủ 21 ngày từ kết quả hợp lệ gần nhất.");
        }
        return new StartEligibility(profile, baseline);
    }

    private record StartPlan(AssessmentStateResponse replay, AssessmentPaper paper) {}
    private record StartEligibility(UserAssessmentProgressDto profile, Optional<AssessmentBaselineDto> baseline) {}

    public AssessmentStateResponse state(String user,String id,String binding) {
        return taking(user,id,binding,(a,at)->AssessmentMapper.state(a,at,development()));
    }
    public AssessmentStateResponse answer(String user,String id,String binding,AssessmentAnswerRequest request,boolean advance) {
        return taking(user,id,binding,(a,at)->{
            if(!AssessmentStatus.ACTIVE.equals(a.getStatus())) return AssessmentMapper.state(a,at,development());
            String fingerprint=request.question()+":"+request.sequence()+":"+request.choice();
            if(advance&&a.getCommands().containsKey(request.commandId())) {
                if(!fingerprint.equals(a.getCommands().get(request.commandId()))) fail(409,"COMMAND_CONFLICT","Yêu cầu lặp có nội dung khác.");
                return AssessmentMapper.state(a,at,development());
            }
            if(AssessmentPhase.READING_PREVIEW.equals(a.getPhase())) fail(409,"PREVIEW_ONLY","Chưa đến thời gian trả lời.");
            boolean skipBlank = advance && modern(a) && a.getPhase() == AssessmentPhase.LISTENING_WAIT
                    && unit(a).getKind() == AssessmentUnitKind.LISTENING_SINGLE
                    && request.question() == a.getCursor() && request.choice() == null;
            if(!skipBlank && !editable(a,request.question())) fail(409,"QUESTION_LOCKED","Câu hỏi không thuộc phần hoặc nhóm đang được sửa.");
            if (advance && modern(a) && unit(a).getKind() != AssessmentUnitKind.LISTENING_SINGLE)
                fail(409,"STAGE_ACTION_REQUIRED","Dùng chuyển nhóm hoặc nộp bài; không chuyển từng câu ở phần này.");
            String key=""+request.question();
            long previous=a.getAnswerSequences().getOrDefault(key,-1L);
            if(request.sequence()<previous || request.sequence()==previous&&!Objects.equals(a.getAnswers().get(key),request.choice()))
                fail(409,"STALE_ANSWER","Lựa chọn mới hơn đã được lưu. Hãy tải trạng thái hiện tại.");
            a.getAnswerSequences().put(key,request.sequence());
            if(request.choice()==null) a.getAnswers().remove(key); else a.getAnswers().put(key,request.choice());
            if(advance) {
                a.getCommands().put(request.commandId(),fingerprint);
                next(a,at);
                if(AssessmentPhase.FINISHED.equals(a.getPhase())) finish(a,a.getNextDueAt());
            }
            return AssessmentMapper.state(a,at,development());
        });
    }
    public AssessmentStateResponse finishStage(String user,String id,String binding,AssessmentFinishStageRequest request) {
        return taking(user,id,binding,(a,at)->{
            if(!AssessmentStatus.ACTIVE.equals(a.getStatus())) return AssessmentMapper.state(a,at,development());
            String fingerprint="finish-stage:"+request.unitIndex()+":"+request.phase();
            String previous=a.getCommands().get(request.commandId());
            if(previous!=null) {
                if(!fingerprint.equals(previous)) fail(409,"COMMAND_CONFLICT","Yêu cầu lặp có nội dung khác.");
                return AssessmentMapper.state(a,at,development());
            }
            boolean phaseMatches = request.phase().equals(a.getPhase().name());
            boolean canFinish = modern(a)
                    ? unit(a).getKind() == AssessmentUnitKind.LISTENING_PASSAGE && listeningWindow(a)
                        && (phaseMatches || ("LISTENING_WAIT".equals(request.phase()) && a.getPhase() == AssessmentPhase.ANSWER))
                    : phaseMatches && a.getPhase() == AssessmentPhase.READING_PREVIEW;
            if (request.unitIndex() != a.getUnitIndex() || !canFinish)
                fail(409,"STAGE_CLOSED","Giai đoạn này đã kết thúc. Hãy tiếp tục theo trạng thái hiện tại.");
            a.getCommands().put(request.commandId(),fingerprint);
            AssessmentTimeline.finishStage(a,at);
            return AssessmentMapper.state(a,at,development());
        });
    }
    public AssessmentStateResponse submit(String user,String id,String binding) {
        return taking(user,id,binding,(a,at)->{
            if(AssessmentStatus.ACTIVE.equals(a.getStatus())) finish(a,at);
            return AssessmentMapper.state(a,at,development());
        });
    }
    public AssessmentStateResponse abandon(String user,String id) {
        return transactions.execute(user,()->{
            var a=owned(user,id);Instant now=now();settleAndGrade(a,now);
            if(AssessmentStatus.ACTIVE.equals(a.getStatus())) {a.setStatus(AssessmentStatus.ABANDONED);a.setSubmittedAt(now);}
            store.save(a);return AssessmentMapper.state(a,now,development());
        });
    }
    public AssessmentMediaResponse audio(String user, String id, String binding, AssessmentAudioRequest command) {
        var source = taking(user, id, binding, (attempt, at) -> {
            var unit = availableAudio(attempt, command);
            return new AudioSource(unit, audioLimit(attempt, unit));
        });
        var delivery = media.delivery(source.unit(), source.limitSeconds());
        return taking(user, id, binding, (attempt, at) -> {
            availableAudio(attempt, command);
            attempt.getAudioIssues().put(Integer.toString(command.unitIndex()), command.issueId());
            return delivery;
        });
    }

    private AssessmentUnitVo availableAudio(AssessmentAttempt attempt, AssessmentAudioRequest command) {
        if (!AssessmentStatus.ACTIVE.equals(attempt.getStatus()) || command.unitIndex() != attempt.getUnitIndex()
                || !AssessmentPhase.LISTENING_LEAD.equals(attempt.getPhase())
                && !AssessmentPhase.ANSWER.equals(attempt.getPhase())
                && !AssessmentPhase.LISTENING_WAIT.equals(attempt.getPhase())) {
            fail(409, "AUDIO_CLOSED", "Âm thanh không còn trong giai đoạn được phát.");
        }
        var unit = unit(attempt);
        if (!unit.getKind().name().startsWith("LISTENING")) {
            fail(409, "NO_AUDIO", "Câu hiện tại không có âm thanh.");
        }
        String issued = attempt.getAudioIssues().get(Integer.toString(command.unitIndex()));
        if (issued != null && !issued.equals(command.issueId())) {
            fail(409, "AUDIO_ALREADY_ISSUED", "Âm thanh đã được cấp một lần.");
        }
        return unit;
    }

    private record AudioSource(AssessmentUnitVo unit, int limitSeconds) {}

    public AssessmentMediaResponse reviewAudio(String user,String id,int index) {
        var a=owned(user,id);
        if(!AssessmentStatus.READY.equals(a.getStatus())) fail(409,"REVIEW_UNAVAILABLE","Bài này không có nội dung xem lại.");
        if(index<0||index>=a.getPaper().getUnits().size()) fail(404,"AUDIO_NOT_FOUND","Không có âm thanh.");
        var u=a.getPaper().getUnits().get(index);
        if (!u.hasAudio()) fail(404,"AUDIO_NOT_FOUND","Không có âm thanh.");
        return media.delivery(u, audioLimit(a, u));
    }
    private int audioLimit(AssessmentAttempt attempt, AssessmentUnitVo unit) {
        if (unit.getKind() != AssessmentUnitKind.LISTENING_SINGLE) return 120;
        return modern(attempt) ? AssessmentTimeline.listeningSingleAudioLimitSeconds() : 50;
    }

    public List<AssessmentHistoryItemResponse> history(String user,int page,int size) {
        tickUser(user);
        return store.history(user,page,size,development()).stream().map(AssessmentMapper::history).toList();
    }
    public AssessmentResultResponse result(String user,String id) {
        return transactions.execute(user,()->{
            var a=owned(user,id);settleAndGrade(a,now());
            if(!AssessmentStatus.READY.equals(a.getStatus())) fail(409,"RESULT_UNAVAILABLE","Bài này chưa có kết quả hợp lệ: "+a.getStatus());
            boolean full=a.getAnalysisGrants().stream().anyMatch(s->!store.revoked(user,s));
            if(!full) {
                var grant=access.currentGrant(user);
                if(grant.isPresent()) {a.getAnalysisGrants().add(grant.get());full=true;}
            }
            store.save(a);
            return AssessmentMapper.result(a,profile(user),full,development());
        });
    }
    public AssessmentReviewResponse review(String user,String id) {
        var result=result(user,id);
        return AssessmentMapper.review(owned(user,id),result);
    }
    public AssessmentMediaResponse soundCheck() {return media.sample();}
    public AssessmentStateResponse reportAudioFailure(String user,String id,String binding,AssessmentAudioFailureRequest request) {
        return taking(user,id,binding,(attempt,at)->{
            if(AssessmentStatus.ACTIVE.equals(attempt.getStatus()) && attempt.getUnitIndex()==request.unitIndex()
                    && unit(attempt).getKind().name().startsWith("LISTENING")) {
                attempt.getAudioFailures().put(Integer.toString(request.unitIndex()),request.reason());
            }
            return AssessmentMapper.state(attempt,at,development());
        });
    }
    public void expire(String user,String id) {
        transactions.execute(user,()->{var a=owned(user,id);settleAndGrade(a,now());store.save(a);return null;});
    }
    private void tickUser(String user) {
        transactions.execute(user,()->{var a=store.active(user);if(a!=null){settleAndGrade(a,now());store.save(a);}return null;});
    }
    private <T> T taking(String user,String id,String binding,BiFunction<AssessmentAttempt,Instant,T> action) {
        return transactions.execute(user,()->{
            var a=owned(user,id);bind(a,binding);Instant at=now();settleAndGrade(a,at);
            T value=action.apply(a,at);store.save(a);return value;
        });
    }
    private AssessmentAttempt owned(String user,String id) {
        var a=store.attempt(id,user);
        if(a==null||!development()&&a.getPaper().isDemonstration()) fail(404,"ATTEMPT_NOT_FOUND","Không tìm thấy bài đánh giá.");
        return a;
    }
    private void bind(AssessmentAttempt a,String binding) {
        if(binding==null||!MessageDigest.isEqual(a.getBindingHash().getBytes(StandardCharsets.UTF_8),hash(binding).getBytes(StandardCharsets.UTF_8)))
            fail(409,"DOCUMENT_MISMATCH","Bài chỉ được làm tại trang đã bắt đầu; không khôi phục trên trang mới.");
    }
    private void settleAndGrade(AssessmentAttempt a,Instant now) {
        if(!AssessmentStatus.ACTIVE.equals(a.getStatus())) return;
        settle(a,now);
        if(AssessmentPhase.FINISHED.equals(a.getPhase())) finish(a,a.getNextDueAt());
    }
    private void finish(AssessmentAttempt a,Instant at) {
        if(!AssessmentStatus.ACTIVE.equals(a.getStatus())) return;
        AssessmentGrading.grade(a,at);
        if(AssessmentStatus.READY.equals(a.getStatus())) {
            String user = a.getUserId();
            var previous = userAssessmentProgress.find(user, development())
                    .orElseThrow(UserLevelPromotionException::new);
            String highest = AssessmentGrading.highest(previous.highestLevel(), a.getLevel());
            boolean latest = previous.latestValidAt() == null || !at.isBefore(previous.latestValidAt());
            userAssessmentProgress.save(user, new UserAssessmentProgressDto(previous.initialLevel(),
                    latest ? a.getLevel() : previous.currentLevel(), highest,
                    latest ? at : previous.latestValidAt(), latest ? a.getId() : previous.latestValidAttemptId(),
                    previous.developmentData()));
            if (!a.getPaper().isDemonstration()) {
                userLevels.promote(user, CefrLevel.valueOf(highest));
            }
        }
    }
    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private UserAssessmentProgressDto profile(String user) {
        return userAssessmentProgress.find(user, development()).orElse(null);
    }
    private List<AssessmentPaper> papers() {
        return store.published().stream().filter(p->p.getRulesVersion()==2)
                .filter(p->development()||!p.isDemonstration()).toList();
    }
    private Instant now() {return Instant.ofEpochMilli(clock.millis());}
    private static void fail(int status,String code,String message) {throw new AssessmentException(status,code,message);}
}
