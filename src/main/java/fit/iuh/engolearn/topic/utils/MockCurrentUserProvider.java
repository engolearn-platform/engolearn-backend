package fit.iuh.engolearn.topic.utils;

import org.springframework.stereotype.Component;

@Component
public class MockCurrentUserProvider implements CurrentUserProvider {
    private static final String MOCK_USER_ID = "user-mock-001";

    @Override
    public String getCurrentUserId() {
        return MOCK_USER_ID;
    }
}
