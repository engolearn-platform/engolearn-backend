package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionImportRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionStatusRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.ImportResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.PageResponse;

public interface PlacementAdminQuestionService {
    PageResponse search(String search, String skill, String level, String status, int page, int size);
    PlacementAdminQuestionResponse get(String id);
    PlacementAdminQuestionResponse create(PlacementAdminQuestionRequest request);
    PlacementAdminQuestionResponse update(String id, PlacementAdminQuestionRequest request);
    PlacementAdminQuestionResponse changeStatus(String id, PlacementAdminQuestionStatusRequest request);
    ImportResponse importQuestions(PlacementAdminQuestionImportRequest request);
    PlacementAdminQuestionMediaResponse mediaCatalog();
}
