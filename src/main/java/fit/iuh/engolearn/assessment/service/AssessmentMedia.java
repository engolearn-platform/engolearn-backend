package fit.iuh.engolearn.assessment.service;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
public interface AssessmentMedia {
    byte[] read(String relative,String sha256,int maxSeconds);
    AssessmentMediaResponse encode(byte[] bytes);
    AssessmentMediaResponse sample();
    AssessmentMediaResponse delivery(AssessmentUnitVo unit, int maxSeconds);
    String durableReference(AssessmentUnitVo unit);
}
