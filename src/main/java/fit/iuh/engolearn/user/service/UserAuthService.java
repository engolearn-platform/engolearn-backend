package fit.iuh.engolearn.user.service;

import fit.iuh.engolearn.user.model.User;
import jakarta.servlet.http.HttpServletResponse;

public interface UserAuthService {
    User register(String email, String password);
    User login(String email, String password);
    User registerAndIssue(String email, String password, HttpServletResponse response);
    User loginAndIssue(String email, String password, HttpServletResponse response);
    void refresh(String oldToken, HttpServletResponse response);
    void logout(String access, String refresh, HttpServletResponse response);
}
