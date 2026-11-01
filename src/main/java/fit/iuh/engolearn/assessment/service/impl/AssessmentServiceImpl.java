package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.utils.*;

import fit.iuh.engolearn.assessment.service.*;
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
import static fit.iuh.engolearn.assessment.utils.AssessmentTimeline.*;

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
            boolean valid=p!=null&&p.getLatestValidAt()!=null||baseline.isPresent();
            Instant eligible=p==null||p.getLatestValidAt()==null?null:p.getLatestValidAt().plus(Duration.ofDays(21));
            return new AssessmentPreparationResponse(valid,eligible,eligible==null||!now.isBefore(eligible),
                    active==null?availability.checkAvailability(user,QUOTA):null,active==null?null:active.getId(),
                    !papers().isEmpty(),development(),p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.getInitialLevel(),
                    p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.getCurrentLevel(),
                    p==null?baseline.map(AssessmentBaselineDto::level).orElse(null):p.getHighestLevel());
        });
    }
    public AssessmentStateResponse start(String user,AssessmentStartRequest command) {
        tickUser(user);
        return transactions.execute(user,()->{
            Instant now=now();
            var previous=store.request(user,command.requestId());
            if(previous!=null) {
                bind(previous,command.binding());
                settleAndGrade(previous,now);store.save(previous);
                return AssessmentMapper.state(previous,now,development());
            }
            var active=store.active(user);
            if(active!=null) {
                fail(409,"ACTIVE_ATTEMPT","Bạn đang có bài ở trang khác. Hãy quay lại hoặc xác nhận bỏ bài cũ.");
            }
            var profile=profile(user);
            var baseline=baselines.findValidBaseline(user);
            if((profile==null||profile.getLatestValidAt()==null)&&baseline.isEmpty())
                fail(409,"BASELINE_REQUIRED","Cần có kết quả đầu vào hợp lệ trước khi bắt đầu.");
            if(profile!=null&&profile.getLatestValidAt()!=null&&now.isBefore(profile.getLatestValidAt().plus(Duration.ofDays(21))))
                fail(409,"COOLDOWN","Chưa đủ 21 ngày từ kết quả hợp lệ gần nhất.");
            var paper=selection.choose(papers(),store.valid(user).stream().filter(a->development()||!a.getPaper().isDemonstration()).toList(),now);
            selection.validate(paper);
            var a=new AssessmentAttempt();
            a.setId(UUID.randomUUID().toString());a.setUserId(user);a.setRequestId(command.requestId());
            a.setBindingHash(hash(command.binding()));a.setStartedAt(now);a.setPaper(AssessmentMapper.snapshot(paper));open(a,0,now);
            access.currentGrant(user).ifPresent(a.getAnalysisGrants()::add);
            var consumed=consumption.consume(new UsageCommandDto(user,QUOTA,1,"assessment",
                    "pfc:"+command.requestId(),a.getId(),now));
            if(!consumed.accepted()) fail(409,"QUOTA_EXHAUSTED","Đã hết lượt đánh giá trong tháng.");
            if(profile==null) {
                profile=new AssessmentProfile();profile.setUserId(user);
                profile.setDevelopmentData(development());
                String initial=baseline.orElseThrow().level();
                profile.setInitialLevel(initial);profile.setCurrentLevel(initial);profile.setHighestLevel(initial);
                store.saveProfile(profile);
            }
            store.save(a);
            return AssessmentMapper.state(a,now,development());
        });
    }
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
            if(request.question()!=a.getCursor()||request.question()>last(a)) fail(409,"QUESTION_LOCKED","Chỉ được thay đổi câu hiện tại.");
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
            if(request.unitIndex()!=a.getUnitIndex()||!request.phase().equals(a.getPhase().name())
                    ||!AssessmentPhase.READING_PREVIEW.equals(a.getPhase()))
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
    public AssessmentMediaResponse audio(String user,String id,String binding,AssessmentAudioRequest command) {
        return taking(user,id,binding,(a,at)->{
            if(!AssessmentStatus.ACTIVE.equals(a.getStatus())||command.unitIndex()!=a.getUnitIndex()
                    ||!AssessmentPhase.LISTENING_LEAD.equals(a.getPhase())&&!AssessmentPhase.ANSWER.equals(a.getPhase()))
                fail(409,"AUDIO_CLOSED","Âm thanh không còn trong giai đoạn được phát.");
            var u=unit(a);
            if(!u.getKind().name().startsWith("LISTENING")) fail(409,"NO_AUDIO","Câu hiện tại không có âm thanh.");
            String key=""+a.getUnitIndex();
            String issued=a.getAudioIssues().get(key);
            if(issued!=null&&!issued.equals(command.issueId())) fail(409,"AUDIO_ALREADY_ISSUED","Âm thanh đã được cấp một lần.");
            byte[] bytes=media.read(u.getAudioPath(),u.getAudioSha256(),u.getKind().name().endsWith("SINGLE")?50:120);
            a.getAudioIssues().put(key,command.issueId());
            return media.encode(bytes);
        });
    }
    public AssessmentMediaResponse reviewAudio(String user,String id,int index) {
        var a=owned(user,id);
        if(!AssessmentStatus.READY.equals(a.getStatus())) fail(409,"REVIEW_UNAVAILABLE","Bài này không có nội dung xem lại.");
        if(index<0||index>=a.getPaper().getUnits().size()) fail(404,"AUDIO_NOT_FOUND","Không có âm thanh.");
        var u=a.getPaper().getUnits().get(index);
        if(u.getAudioPath()==null) fail(404,"AUDIO_NOT_FOUND","Không có âm thanh.");
        return media.encode(media.read(u.getAudioPath(),u.getAudioSha256(),u.getKind().name().endsWith("SINGLE")?50:120));
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
            return AssessmentMapper.result(a,store.profile(user),full,development());
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
            var p=store.profile(a.getUserId());
            if(p==null) throw new IllegalStateException("Missing assessment profile");
            p.setHighestLevel(AssessmentGrading.highest(p.getHighestLevel(),a.getLevel()));
            if(p.getLatestValidAt()==null||!at.isBefore(p.getLatestValidAt())) {
                p.setCurrentLevel(a.getLevel());p.setLatestValidAt(at);p.setLatestValidAttemptId(a.getId());
            }
            store.saveProfile(p);
        }
    }
    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private AssessmentProfile profile(String user) {
        var p=store.profile(user);
        return p!=null&&p.isDevelopmentData()&&!development()?null:p;
    }
    private List<AssessmentPaper> papers() {
        return store.published().stream().filter(p->development()||!p.isDemonstration()).toList();
    }
    private Instant now() {return Instant.ofEpochMilli(clock.millis());}
    private static void fail(int status,String code,String message) {throw new AssessmentException(status,code,message);}
}
