package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.*;
import java.time.Instant;

public final class LearningPathDayAvailability {
    private LearningPathDayAvailability() {}

    public static boolean available(LearningPath path, LearningPathWeek week,
                                    LearningPathDay day, Instant now) {
        return Boolean.TRUE.equals(path.getActive())
                && (LearningPathStatus.NOT_STARTED == path.getStatus() || LearningPathStatus.IN_PROGRESS == path.getStatus())
                && path.getNextRunAt() != null && path.getNextRunAt().toInstant().isAfter(now)
                && path.getInactivity() != null && path.getInactivity().getCancelAt() != null
                && path.getInactivity().getCancelAt().toInstant().isAfter(now)
                && LearningPathWeekStatus.OPEN == week.getStatus()
                && week.getCloseAt() != null && week.getCloseAt().toInstant().isAfter(now)
                && (LearningPathDayStatus.NOT_STARTED == day.getStatus() || LearningPathDayStatus.IN_PROGRESS == day.getStatus())
                && (LearningPathDayStatus.IN_PROGRESS != day.getStatus()
                || (day.getAutoCloseAt() != null && day.getAutoCloseAt().toInstant().isAfter(now)));
    }
}
