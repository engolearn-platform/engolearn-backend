package fit.iuh.engolearn.user.service;

import fit.iuh.engolearn.user.dto.request.ManagementRequests.*;
import fit.iuh.engolearn.user.dto.response.ManagementResponses.*;

public interface UserManagementService {
    ListResponse list(ListRequest request);
    DetailResponse detail(String id);
    DetailResponse create(CreateRequest request);
    DetailResponse edit(String id, EditRequest request);
    DetailResponse status(String id, StatusRequest request);
    DetailResponse role(String id, RoleRequest request);
    DeletedResponse delete(String id, long revision);
}
