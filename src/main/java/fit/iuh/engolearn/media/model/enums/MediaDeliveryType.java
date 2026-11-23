package fit.iuh.engolearn.media.model.enums;

/** Provider delivery type persisted with an asset, not exposed to consuming features. */
public enum MediaDeliveryType {
    UPLOAD("upload"),
    AUTHENTICATED("authenticated");

    private final String cloudinaryValue;

    MediaDeliveryType(String cloudinaryValue) {
        this.cloudinaryValue = cloudinaryValue;
    }

    public String cloudinaryValue() {
        return cloudinaryValue;
    }
}
