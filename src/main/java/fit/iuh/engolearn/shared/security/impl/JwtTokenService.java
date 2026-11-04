package fit.iuh.engolearn.shared.security.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtTokenService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] secret;
    private final String issuer;

    public JwtTokenService(@Value("${engolearn.security.jwt.secret:local-development-key-change-me-32bytes-minimum}") String secret,
                          @Value("${engolearn.security.jwt.issuer:engolearn}") String issuer) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8); this.issuer = issuer;
    }

    public String create(String accountId, String sessionId, String type, long seconds) {
        long now = Instant.now().getEpochSecond();
        String payload = "{\"iss\":\"" + issuer + "\",\"sub\":\"" + accountId + "\",\"sid\":\"" + sessionId
                + "\",\"jti\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type + "\",\"iat\":" + now + ",\"exp\":" + (now + seconds) + "}";
        String unsigned = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
    }

    public Claims verify(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.");
            String header = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!header.equals("{\"alg\":\"HS256\",\"typ\":\"JWT\"}")) throw new IllegalArgumentException();
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsigned), DECODER.decode(parts[2]))) throw new IllegalArgumentException();
            String json = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            String iss = value(json, "iss"), sub = value(json, "sub"), sid = value(json, "sid"), type = value(json, "type");
            if (!issuer.equals(iss) || !expectedType.equals(type) || number(json, "exp") <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            return new Claims(sub, sid, type);
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid token"); }
    }

    public String hash(String token) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private byte[] sign(String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String value(String json, String key) { Matcher m = Pattern.compile("\\\"" + key + "\\\":\\\"([^\\\"]*)\\\"").matcher(json); if (!m.find()) throw new IllegalArgumentException(); return m.group(1); }
    private static long number(String json, String key) { Matcher m = Pattern.compile("\\\"" + key + "\\\":([0-9]+)").matcher(json); if (!m.find()) throw new IllegalArgumentException(); return Long.parseLong(m.group(1)); }
    public record Claims(String accountId, String sessionId, String type) { }
}
