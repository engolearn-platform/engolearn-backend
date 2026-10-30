package fit.iuh.engolearn.quota;

import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.model.QuotaCalendar;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QuotaCalendarTest {
    @Test void calendarWindowsAreHalfOpenAndUseVietnamCalendarIncludingLeapMonth() {
        assertWindow("2028-02-29T16:59:59Z", "DAILY", "2028-02-28T17:00:00Z", "2028-02-29T17:00:00Z");
        assertWindow("2028-02-29T17:00:00Z", "DAILY", "2028-02-29T17:00:00Z", "2028-03-01T17:00:00Z");
        assertWindow("2028-02-29T12:00:00Z", "MONTHLY", "2028-01-31T17:00:00Z", "2028-02-29T17:00:00Z");
        assertWindow("2026-11-01T16:59:59Z", "WEEKLY", "2026-10-25T17:00:00Z", "2026-11-01T17:00:00Z");
        assertWindow("2026-11-01T17:00:00Z", "WEEKLY", "2026-11-01T17:00:00Z", "2026-11-08T17:00:00Z");
    }

    private void assertWindow(String at, String period, String start, String end) {
        QuotaCalendar.Window actual = QuotaCalendar.window(Instant.parse(at), period);
        assertThat(actual.start()).isEqualTo(Instant.parse(start));
        assertThat(actual.end()).isEqualTo(Instant.parse(end));
    }

    @Test void inProcessContractRejectsMalformedShape() {
        assertThatThrownBy(() -> new UsageCommandDto(" ", "TYPE", 1, "CALLER", "key", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsageCommandDto("u", "TYPE", 0, "CALLER", "key", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsageCommandDto("u", "TYPE", 9_007_199_254_740_992L, "CALLER", "key", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingTransactionManagerRefusesOperationBeforeAnyWork() {
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var transactions = new fit.iuh.engolearn.quota.service.impl.QuotaTransactionsImpl(
                beans.getBeanProvider(org.springframework.data.mongodb.MongoTransactionManager.class));
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(() -> transactions.execute(() -> { called.set(true); return true; }))
                .isInstanceOf(fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException.class);
        assertThat(called).isFalse();
    }
}
