package fit.iuh.engolearn.user.controller;

import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.user.dto.request.ManagementRequests.*;
import fit.iuh.engolearn.user.dto.response.ManagementResponses.*;
import fit.iuh.engolearn.user.exception.UserManagementFailure;
import fit.iuh.engolearn.user.service.UserManagementService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService service;

    @ModelAttribute
    void queryShape(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var allowed = switch (request.getMethod()) {
            case "GET", "HEAD" -> request.getRequestURI().matches(".*/api/v1/admin/users/?")
                    ? Set.of("search","role","status","sort","page","size") : Set.<String>of();
            case "DELETE" -> Set.of("expectedRevision");
            default -> Set.<String>of();
        };
        if (!allowed.containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) throw invalid();
    }

    @GetMapping({"", "/"})
    public ApiResponse<ListResponse> list(HttpServletRequest request) {
        ListRequest query;
        try {
            query = ListRequest.fromQuery(param(request,"search",""), request.getParameter("role"), request.getParameter("status"),
                    param(request,"sort","createdAt,desc"), param(request,"page","0"), param(request,"size","25"));
        } catch (IllegalArgumentException invalidValue) { throw invalid(); }
        return ApiResponse.ok(service.list(query));
    }

    private static String param(HttpServletRequest request, String name, String fallback) {
        var value = request.getParameter(name);
        return value == null ? fallback : value;
    }
    private static UserManagementFailure invalid() {
        return new UserManagementFailure(UserManagementFailure.Reason.VALIDATION_ERROR);
    }

    @GetMapping("/{id}")
    public ApiResponse<DetailResponse> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DetailResponse> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DetailResponse> edit(@PathVariable String id, @Valid @RequestBody EditRequest request) {
        return ApiResponse.ok(service.edit(id, request));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<DetailResponse> role(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(service.role(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DetailResponse> status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(service.status(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeletedResponse> delete(@PathVariable String id, @RequestParam long expectedRevision) {
        return ApiResponse.ok(service.delete(id, expectedRevision));
    }
}
