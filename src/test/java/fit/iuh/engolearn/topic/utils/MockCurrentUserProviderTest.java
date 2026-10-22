package fit.iuh.engolearn.topic.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockCurrentUserProviderTest {

    @Test
    void returnsTheFixedMockUserId() {
        CurrentUserProvider provider = new MockCurrentUserProvider();

        assertThat(provider.getCurrentUserId()).isEqualTo("user-mock-001");
    }
}
