package fit.iuh.engolearn.assessment.utils;

import fit.iuh.engolearn.assessment.contract.exception.AssessmentHistoryInvalidQueryException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

public final class AssessmentHistoryCursorCodec {
    private AssessmentHistoryCursorCodec() {}

    public record Position(Instant submittedAt, String attemptId) {}

    public static String encode(String owner, boolean demonstration, int limit, Position position) {
        String payload = "v1|" + fingerprint(owner, demonstration, limit) + "|"
                + position.submittedAt() + "|" + position.attemptId();
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        if (cursor.length() > 512) throw new IllegalStateException("Assessment cursor exceeds limit");
        return cursor;
    }

    public static Position decode(String cursor, String owner, boolean demonstration, int limit) {
        if (cursor.isBlank() || cursor.length() > 512) throw new AssessmentHistoryInvalidQueryException();
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", 4);
            if (parts.length != 4 || !"v1".equals(parts[0])
                    || !fingerprint(owner, demonstration, limit).equals(parts[1]) || parts[3].isBlank()) {
                throw new AssessmentHistoryInvalidQueryException();
            }
            Instant at = Instant.parse(parts[2]);
            if (!at.equals(Instant.ofEpochMilli(at.toEpochMilli()))) throw new AssessmentHistoryInvalidQueryException();
            return new Position(at, parts[3]);
        } catch (RuntimeException ex) {
            throw new AssessmentHistoryInvalidQueryException();
        }
    }

    private static String fingerprint(String owner, boolean demonstration, int limit) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((demonstration + "|" + limit + "|" + owner).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}