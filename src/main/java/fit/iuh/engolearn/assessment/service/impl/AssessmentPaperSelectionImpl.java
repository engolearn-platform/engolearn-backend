package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;
import fit.iuh.engolearn.assessment.model.enums.*;

import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentPaperSelectionImpl implements AssessmentPaperSelection {
    private final AssessmentMedia media;
    public void validate(AssessmentPaper p) {
        try {validateDefinition(p);}
        catch(NullPointerException | IllegalArgumentException malformed) {invalid();}
    }
    private void validateDefinition(AssessmentPaper p) {
        if(p.getId()==null||p.getId().isBlank()||p.getSeries()==null||p.getSeries().isBlank()||p.getVersion()<1) invalid();
        if(p.getSkills()==null || p.getSkills().size()!=5 || p.getUnits()==null || p.getUnits().size()!=26) invalid();
        Map<String,Integer> counts=new HashMap<>();
        Set<String> codes=new HashSet<>();
        Set<String> audioPaths=new HashSet<>();
        if(p.getSkills().stream().filter(s->AssessmentSection.READING.equals(s.getSection())).count()!=3
                ||p.getSkills().stream().filter(s->AssessmentSection.LISTENING.equals(s.getSection())).count()!=2) invalid();
        for(var skill:p.getSkills()) if(skill.getCode()==null || !codes.add(skill.getCode()) || skill.getName()==null || skill.getName().isBlank()) invalid();
        int number=1;
        for(int i=0;i<26;i++) {
            var u=p.getUnits().get(i);
            String kind=i<10?"READING_SINGLE":i<14?"READING_PASSAGE":i<24?"LISTENING_SINGLE":"LISTENING_PASSAGE";
            if(!AssessmentUnitKind.valueOf(kind).equals(u.getKind()) || u.getQuestions()==null || u.getQuestions().size()!=(kind.endsWith("SINGLE")?1:5)) invalid();
            int[] levels=new int[4];
            for(var q:u.getQuestions()) {
                if(q.getNumber()!=number++ || q.getPrompt()==null || q.getPrompt().isBlank() || q.getOptions()==null || q.getOptions().size()!=4
                        || q.getOptions().stream().anyMatch(s->s==null||s.isBlank()) || q.getCorrectOption()<0 || q.getCorrectOption()>3
                        ||q.getExplanation()==null || q.getExplanation().isBlank() || !codes.contains(q.getSkillCode())) invalid();
                var skill=p.getSkills().stream().filter(s->s.getCode().equals(q.getSkillCode())).findFirst().orElseThrow();
                if(!skill.getSection().equals(q.getNumber()<=30?AssessmentSection.READING:AssessmentSection.LISTENING)) invalid();
                int level=List.of("A1","A2","B1","B2").indexOf(q.getLevel());
                if(level<0) invalid();
                levels[level]++;
                counts.merge(q.getSkillCode(),1,Integer::sum);
            }
            if(kind.endsWith("PASSAGE") && (u.getPassage()==null || u.getPassage().isBlank()) && kind.startsWith("READING")) invalid();
            if(i>=14) {
                if(u.getAudioPath()==null || !audioPaths.add(u.getAudioPath()) || u.getAudioSha256()==null || u.getAudioSha256().length()!=64
                        ||u.getQuestions().stream().anyMatch(q->q.getTranscript()==null||q.getTranscript().isBlank())) invalid();
                media.read(u.getAudioPath(),u.getAudioSha256(),kind.endsWith("SINGLE")?50:120);
            }
            if(i>=10 && i<=13 && !Arrays.equals(levels,new int[][]{{5,0,0,0},{0,5,0,0},{0,1,4,0},{0,0,1,4}}[i-10])) invalid();
            if(i>=24 && !Arrays.equals(levels,new int[][]{{1,3,1,0},{0,0,3,2}}[i-24])) invalid();
        }
        if(counts.size()!=5 || counts.values().stream().anyMatch(n->n!=10)) invalid();
        checkSingleLevels(p,0,new int[]{1,2,3,4});
        checkSingleLevels(p,14,new int[]{2,2,3,3});
    }
    private void checkSingleLevels(AssessmentPaper p,int start,int[] expected) {
        int[] actual=new int[4];
        for(int i=start;i<start+10;i++) actual[List.of("A1","A2","B1","B2").indexOf(p.getUnits().get(i).getQuestions().getFirst().getLevel())]++;
        if(!Arrays.equals(actual,expected)) invalid();
    }
    private void invalid() {throw new AssessmentException(503,"PAPER_INVALID","Bộ đề chưa đủ cấu trúc hoặc tài nguyên hợp lệ");}
    public AssessmentPaper choose(List<AssessmentPaper> papers,List<AssessmentAttempt> history,Instant now) {
        if(papers.isEmpty()) throw new AssessmentException(503,"NO_PAPER","Chưa có bộ đề đang sử dụng");
        Set<String> recent=new LinkedHashSet<>();
        Map<String,Instant> seriesLast=new HashMap<>(),skillLast=new HashMap<>();
        for(var a:history) {
            if(recent.size()<3) recent.add(a.getPaper().getSeries());
            seriesLast.merge(a.getPaper().getSeries(),a.getSubmittedAt(),(x,y)->x.isAfter(y)?x:y);
            for(var s:a.getSkillResults()) skillLast.merge(s.getCode(),a.getSubmittedAt(),(x,y)->x.isAfter(y)?x:y);
        }
        var outside=papers.stream().filter(p->!recent.contains(p.getSeries())).toList();
        if(outside.isEmpty()) {
            Instant oldest=papers.stream().map(p->seriesLast.getOrDefault(p.getSeries(),Instant.EPOCH)).min(Instant::compareTo).orElseThrow();
            return random(papers.stream().filter(p->seriesLast.getOrDefault(p.getSeries(),Instant.EPOCH).equals(oldest)).toList());
        }
        int bestUnmeasured=outside.stream().mapToInt(p->(int)p.getSkills().stream().filter(s->!skillLast.containsKey(s.getCode())).count()).max().orElseThrow();
        var candidates=outside.stream().filter(p->p.getSkills().stream().filter(s->!skillLast.containsKey(s.getCode())).count()==bestUnmeasured).toList();
        double best=candidates.stream().mapToDouble(p->age(p,skillLast,now)).max().orElseThrow();
        return random(candidates.stream().filter(p->Double.compare(age(p,skillLast,now),best)==0).toList());
    }
    private double age(AssessmentPaper p,Map<String,Instant> last,Instant now) {
        return p.getSkills().stream().filter(s->last.containsKey(s.getCode())).mapToDouble(s->Math.max(0,now.getEpochSecond()-last.get(s.getCode()).getEpochSecond())).sum();
    }
    private AssessmentPaper random(List<AssessmentPaper> values) {return values.get(ThreadLocalRandom.current().nextInt(values.size()));}
}
