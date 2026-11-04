package fit.iuh.engolearn.shared.security.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private final JwtTokenService tokens = new JwtTokenService("0123456789abcdef0123456789abcdef", "engolearn");

    @Test
    void tokenMustMatchExpectedTypeAndIssuer() {
        String token = tokens.create("account", "session", "access", 60);
        assertThat(tokens.verify(token, "access").accountId()).isEqualTo("account");
        assertThatThrownBy(() -> tokens.verify(token, "refresh")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenService("0123456789abcdef0123456789abcdef", "other").verify(token, "access"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alteredSignatureIsRejected() {
        String token = tokens.create("account", "session", "refresh", 60);
        assertThatThrownBy(() -> tokens.verify(token.substring(0, token.length() - 2) + "aa", "refresh"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
