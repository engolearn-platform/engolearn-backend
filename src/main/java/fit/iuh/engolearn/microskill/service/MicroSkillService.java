package fit.iuh.engolearn.microskill.service;
import fit.iuh.engolearn.microskill.dto.request.*;
import fit.iuh.engolearn.microskill.dto.response.*;
public interface MicroSkillService {
    MicroSkillListResponse list();
    MicroSkillResponse detail(String id);
    MicroSkillResponse create(CreateMicroSkillRequest request);
    MicroSkillResponse update(String id, UpdateMicroSkillRequest request);
    DeleteMicroSkillResponse delete(String id, int revision);
}
