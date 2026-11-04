package fit.iuh.engolearn.user.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import static org.assertj.core.api.Assertions.assertThat;

class UserMappingTest {
    @Test
    void mapsUserToUsersCollectionWithStableAuthFields() throws Exception {
        assertThat(User.class.getAnnotation(Document.class).value()).isEqualTo("users");
        assertThat(User.class.getDeclaredField("email").getAnnotation(Field.class).value()).isEqualTo("email");
        assertThat(User.class.getDeclaredField("email").getAnnotation(Indexed.class).unique()).isTrue();
        assertThat(User.class.getDeclaredField("passwordHash").getAnnotation(Field.class).value()).isEqualTo("password_hash");
        assertThat(User.class.getDeclaredField("role").getAnnotation(Field.class).value()).isEqualTo("role");
        assertThat(User.class.getDeclaredField("locked").getAnnotation(Field.class).value()).isEqualTo("locked");
    }
}
