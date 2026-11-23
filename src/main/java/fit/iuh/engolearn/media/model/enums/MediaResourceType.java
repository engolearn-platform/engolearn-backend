package fit.iuh.engolearn.media.model.enums;

/** Cloudinary resource categories required for delivery and compensation. */
public enum MediaResourceType {
    IMAGE("image"),
    VIDEO("video");

    private final String cloudinaryValue;

    MediaResourceType(String cloudinaryValue) {
        this.cloudinaryValue = cloudinaryValue;
    }

    public String cloudinaryValue() {
        return cloudinaryValue;
    }
}
