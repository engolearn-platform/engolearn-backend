package fit.iuh.engolearn.companion.service;

/** Authoritative cumulative token usage reported for one successful model round. */
public record CompanionUsage(long totalTokenCount) {
    public CompanionUsage {
        if (totalTokenCount < 0) {
            throw new IllegalArgumentException("Total token count cannot be negative");
        }
    }

    @Override
    public String toString() {
        return "CompanionUsage[totalTokenCount=redacted]";
    }
}
