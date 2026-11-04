package fit.iuh.engolearn.speaking.model;

public record SpeakingGoal(String id, String title, String description, Priority priority) {
    public enum Priority { REQUIRED, OPTIONAL }
}
