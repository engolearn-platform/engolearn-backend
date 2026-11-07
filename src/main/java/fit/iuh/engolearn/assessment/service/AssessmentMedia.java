package fit.iuh.engolearn.assessment.service;
import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public interface AssessmentMedia {
    byte[] read(String relative,String sha256,int maxSeconds);
    AssessmentMediaResponse encode(byte[] bytes);
    AssessmentMediaResponse sample();
}
