package fit.iuh.engolearn.quota.model;

import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import java.time.Instant;

public record CounterView(long used, Instant start, Instant end, Instant allowanceStart, String allowanceId) {
    public static CounterView from(QuotaUsageCounter counter, String unit, String period, Instant at) {
        QuotaCalendar.Window window = QuotaCalendar.window(at, period);
        if (counter == null) return new CounterView(0, window.start(), window.end(), window.start(), null);
        long used = counter.getUsedAmount();
        Instant start = counter.getCalendarWindowStartAt(), end = counter.getCalendarWindowEndAt();
        Instant allowanceStart = counter.getAllowanceStartAt();
        if (used < 0 || used > 9_007_199_254_740_991L || !unit.equals(counter.getUnit()) || !period.equals(counter.getPeriod().name())
                || start == null || end == null || allowanceStart == null || counter.getAllowanceId() == null
                || counter.getRevision() < 0 || !end.isAfter(start) || allowanceStart.isBefore(start) || !allowanceStart.isBefore(end)
                || !QuotaCalendar.window(start, period).start().equals(start)
                || !QuotaCalendar.window(start, period).end().equals(end) || start.isAfter(window.start()))
            throw new QuotaUnavailableException();
        if (!start.equals(window.start())) return new CounterView(0, window.start(), window.end(), window.start(), null);
        return new CounterView(used, start, end, allowanceStart, counter.getAllowanceId());
    }
    public String status(long limit) { return status(used, limit); }
    public static String status(long used, long limit) { return used > limit ? "OVER_LIMIT" : used == limit ? "EXHAUSTED" : "AVAILABLE"; }
}
