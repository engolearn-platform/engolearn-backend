package fit.iuh.engolearn.assessment;
import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.AssessmentPaper;
import java.util.*;

final class AssessmentTestPaper {
    private AssessmentTestPaper() {}
    static AssessmentPaper paper() {return paper("paper-1","series-1","0".repeat(64));}
    static AssessmentPaper paper(String id,String series,String digest) {
        var skills=List.of(new AssessmentSkillVo("R01","Reading details","Example",AssessmentSection.READING),
                new AssessmentSkillVo("R02","Reading gist","Example",AssessmentSection.READING),new AssessmentSkillVo("R04","Reading inference","Example",AssessmentSection.READING),
                new AssessmentSkillVo("L01","Listening gist","Example",AssessmentSection.LISTENING),new AssessmentSkillVo("L03","Listening details","Example",AssessmentSection.LISTENING));
        List<AssessmentUnitVo> units=new ArrayList<>();
        int n=1;
        for(int i=0;i<26;i++) {
            String kind=i<10?"READING_SINGLE":i<14?"READING_PASSAGE":i<24?"LISTENING_SINGLE":"LISTENING_PASSAGE";
            int count=kind.endsWith("SINGLE")?1:5;
            int[] levels=i<10?new int[]{new int[]{0,1,1,2,2,2,3,3,3,3}[i]}:i<14?new int[][]{{0,0,0,0,0},{1,1,1,1,1},{1,2,2,2,2},{2,3,3,3,3}}[i-10]:
                    i<24?new int[]{new int[]{0,0,1,1,2,2,2,3,3,3}[i-14]}:new int[][]{{0,1,1,1,2},{2,2,2,3,3}}[i-24];
            List<AssessmentQuestionVo> questions=new ArrayList<>();
            for(int j=0;j<count;j++,n++) questions.add(new AssessmentQuestionVo(n,new String[]{"A1","A2","B1","B2"}[levels[j]],
                    n<=10?"R01":n<=20?"R02":n<=30?"R04":n<=40?"L01":"L03","Question "+n,List.of("A","B","C","D"),1,"Because B",i>=14?"Speech transcript":null));
            units.add(new AssessmentUnitVo(AssessmentUnitKind.valueOf(kind),kind.equals("READING_PASSAGE")?"A reading passage":null,i>=14?"unit"+i+".wav":null,i>=14?digest:null,questions));
        }
        return AssessmentPaper.builder().id(id).series(series).version(1).published(true).demonstration(true).skills(skills).units(units).build();
    }
}
