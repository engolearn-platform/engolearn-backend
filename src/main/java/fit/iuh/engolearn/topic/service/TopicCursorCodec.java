package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class TopicCursorCodec {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "|";
    private static final String NULL_SORT_KEY = "~";
    private static final int PAYLOAD_PARTS = 5;

    private TopicCursorCodec() {
    }

    /** A decoded cursor; {@code sortKey} is null when the cursor row has no sort key. */
    public record DecodedCursor(TopicSortOrder sort,
                                String filterFingerprint,
                                Integer sortKey,
                                String topicId) {
    }

    public static String encode(TopicSortOrder sort, String filterFingerprint, Integer sortKey, String topicId) {
        if (sort == null || filterFingerprint == null || filterFingerprint.isBlank()
                || topicId == null || topicId.isBlank()) {
            throw new IllegalArgumentException("Cursor fields must be present");
        }
        String payload = VERSION
                + SEPARATOR + sort.apiValue()
                + SEPARATOR + filterFingerprint
                + SEPARATOR + (sortKey == null ? NULL_SORT_KEY : sortKey)
                + SEPARATOR + topicId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static DecodedCursor decode(String cursor) {
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBrowseRequestException("Cursor cannot be decoded");
        }
        String[] parts = payload.split("\\|", PAYLOAD_PARTS);
        if (parts.length != PAYLOAD_PARTS || !VERSION.equals(parts[0]) || parts[2].isBlank() || parts[4].isBlank()) {
            throw new InvalidBrowseRequestException("Cursor cannot be decoded");
        }
        TopicSortOrder sort = TopicSortOrder.fromApiValue(parts[1])
                .orElseThrow(() -> new InvalidBrowseRequestException("Cursor cannot be decoded"));

        Integer sortKey = null;
        if (!NULL_SORT_KEY.equals(parts[3])) {
            try {
                sortKey = Integer.valueOf(parts[3]);
            } catch (NumberFormatException ex) {
                throw new InvalidBrowseRequestException("Cursor cannot be decoded");
            }
        }
        return new DecodedCursor(sort, parts[2], sortKey, parts[4]);
    }

    public static String fingerprint(CefrLevel level, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String source = "level=" + (level == null ? "" : level.name()) + ";keyword=" + normalizedKeyword;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to fingerprint browse filters", ex);
        }
    }
}
