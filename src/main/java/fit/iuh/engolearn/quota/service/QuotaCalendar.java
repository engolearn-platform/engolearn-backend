package fit.iuh.engolearn.quota.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class QuotaCalendar {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private QuotaCalendar() {}

    public static Window window(Instant at, String period) {
        LocalDate date = at.atZone(ZONE).toLocalDate();
        LocalDate start = switch (period) {
            case "DAILY" -> date;
            case "WEEKLY" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTHLY" -> date.withDayOfMonth(1);
            default -> throw new IllegalArgumentException("Unsupported quota period");
        };
        LocalDate end = switch (period) {
            case "DAILY" -> start.plusDays(1);
            case "WEEKLY" -> start.plusWeeks(1);
            default -> start.plusMonths(1);
        };
        return new Window(start.atStartOfDay(ZONE).toInstant(), end.atStartOfDay(ZONE).toInstant());
    }

    public record Window(Instant start, Instant end) {}
}
