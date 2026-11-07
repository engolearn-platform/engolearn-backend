package fit.iuh.engolearn.speaking.model;

import org.springframework.data.mongodb.core.mapping.Field;

public record SpeakingGoal(@Field("id") String id, String title, String description, Priority priority) {
    public enum Priority { REQUIRED, OPTIONAL }
}
